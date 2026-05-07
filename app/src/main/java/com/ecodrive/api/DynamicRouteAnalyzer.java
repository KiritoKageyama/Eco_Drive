package com.ecodrive.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes OSRM route geometry and steps to extract:
 * - Road types (primary/secondary/tertiary/residential)
 * - Segment-level terrain classification
 * - Speed characteristics
 * - Dynamic adjustments per segment
 */
public class DynamicRouteAnalyzer {

    public static class RouteSegment {
        public double startLatitude;
        public double startLongitude;
        public double endLatitude;
        public double endLongitude;
        public double distanceKm;
        public double durationSec;
        public String roadType;        // primary, secondary, tertiary, residential, unclassified, service, track
        public String terrainType;     // flat, hilly, mountainous
        public double speedLimitKmh;   // default or from OSM data
        public double elevationChangeM; // for terrain detection
        public double accelMultiplier;  // region-specific
        public double emissionMultiplier; // road-type specific

        public RouteSegment() {
            this.accelMultiplier = 1.0;
            this.emissionMultiplier = 1.0;
            this.speedLimitKmh = 60.0; // default
        }
    }

    public interface SegmentAnalysisCallback {
        void onSegmentsAnalyzed(List<RouteSegment> segments, double totalEmissionMultiplier);
        void onError(String error);
    }

    /**
     * Fetch OSRM route with detailed steps and classify segments
     */
    public static void analyzeRouteSegments(String startLat, String startLng, 
                                            String endLat, String endLng,
                                            SegmentAnalysisCallback callback) {
        new Thread(() -> {
            try {
                String osrmUrl = String.format(
                    "https://router.project-osrm.org/route/v1/driving/%s,%s;%s,%s?steps=true&geometries=geojson&overview=full&annotations=speed,duration,distance",
                    startLng, startLat, endLng, endLat);

                HttpURLConnection conn = (HttpURLConnection) new URL(osrmUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) {
                    callback.onError("OSRM error: " + conn.getResponseCode());
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                JSONObject result = new JSONObject(response.toString());
                JSONArray routes = result.getJSONArray("routes");
                if (routes.length() == 0) {
                    callback.onError("No route found");
                    return;
                }

                JSONObject bestRoute = routes.getJSONObject(0);

                // OSRM nests steps under routes[0].legs[0].steps (NOT routes[0].steps)
                JSONArray legs = bestRoute.getJSONArray("legs");
                if (legs.length() == 0) {
                    callback.onError("No route legs found");
                    return;
                }

                List<RouteSegment> segments = new ArrayList<>();
                double totalEmissionMultiplier = 0.0;
                int segmentCount = 0;

                // Iterate over all legs and their steps
                for (int legIdx = 0; legIdx < legs.length(); legIdx++) {
                    JSONObject leg = legs.getJSONObject(legIdx);
                    JSONArray steps = leg.optJSONArray("steps");
                    if (steps == null) continue;

                    for (int i = 0; i < steps.length(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        RouteSegment segment = parseOSRMStep(step);
                        segments.add(segment);
                        totalEmissionMultiplier += segment.emissionMultiplier;
                        segmentCount++;
                    }
                }

                if (segmentCount > 0) {
                    totalEmissionMultiplier /= segmentCount;
                } else {
                    totalEmissionMultiplier = 1.0;
                }

                callback.onSegmentsAnalyzed(segments, totalEmissionMultiplier);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static RouteSegment parseOSRMStep(JSONObject step) throws JSONException {
        RouteSegment seg = new RouteSegment();

        try {
            seg.distanceKm = step.getDouble("distance") / 1000.0;
            seg.durationSec = step.getDouble("duration");

            // OSRM provides road type info in multiple places:
            // 1. "ref" field for highway references (NH-48, SH-12)
            // 2. "name" field for road names
            // 3. Maneuver type can hint at road class
            String ref = step.optString("ref", "");
            String name = step.optString("name", "");
            String mode = step.optString("mode", "driving");

            seg.roadType = classifyRoadType(ref, name, seg.distanceKm, seg.durationSec);

            // Determine speed limit based on road type
            seg.speedLimitKmh = getSpeedLimitForRoadType(seg.roadType);

            // Apply multipliers based on road type
            applyRoadTypeMultipliers(seg);

            // Get coordinates from geometry
            if (step.has("geometry")) {
                JSONObject geometry = step.getJSONObject("geometry");
                JSONArray coordinates = geometry.getJSONArray("coordinates");
                if (coordinates.length() >= 2) {
                    JSONArray firstCoord = coordinates.getJSONArray(0);
                    JSONArray lastCoord = coordinates.getJSONArray(coordinates.length() - 1);
                    seg.startLongitude = firstCoord.getDouble(0);
                    seg.startLatitude = firstCoord.getDouble(1);
                    seg.endLongitude = lastCoord.getDouble(0);
                    seg.endLatitude = lastCoord.getDouble(1);
                }
            }
        } catch (JSONException e) {
            // Use defaults
        }

        return seg;
    }

    /**
     * Improved road classification using OSRM's ref field, road name, and speed heuristics.
     * OSRM step names are street names, not OSM highway tags. We use multiple signals.
     */
    private static String classifyRoadType(String ref, String name, double distanceKm, double durationSec) {
        String lowerRef = ref.toLowerCase();
        String lowerName = name.toLowerCase();

        // 1. Check reference field (most reliable for highways)
        if (lowerRef.contains("nh") || lowerRef.contains("expressway") || lowerRef.contains("motorway")
                || lowerRef.startsWith("e ") || lowerRef.matches(".*\\bnh\\s*\\d+.*")) {
            return "motorway";
        }
        if (lowerRef.contains("sh") || lowerRef.matches(".*\\bsh\\s*\\d+.*")) {
            return "primary";
        }

        // 2. Check name for common Indian and global road naming patterns
        if (lowerName.contains("motorway") || lowerName.contains("highway") || lowerName.contains("expressway")
                || lowerName.contains("freeway") || lowerName.contains("bypass")) {
            return "motorway";
        }
        if (lowerName.contains("national") || lowerName.contains("ring road") || lowerName.contains("outer ring")) {
            return "primary";
        }
        if (lowerName.contains("state") || lowerName.contains("main road") || lowerName.contains("trunk")) {
            return "secondary";
        }
        if (lowerName.contains("lane") || lowerName.contains("gali") || lowerName.contains("street")
                || lowerName.contains("marg") || lowerName.contains("colony")) {
            return "residential";
        }
        if (lowerName.contains("service") || lowerName.contains("access") || lowerName.contains("internal")) {
            return "service";
        }

        // 3. Speed-based heuristic as fallback: classify by average speed
        if (durationSec > 0 && distanceKm > 0) {
            double avgSpeedKmh = (distanceKm / durationSec) * 3600.0;
            if (avgSpeedKmh > 80.0) return "motorway";
            if (avgSpeedKmh > 55.0) return "primary";
            if (avgSpeedKmh > 35.0) return "secondary";
            if (avgSpeedKmh > 20.0) return "tertiary";
            return "residential"; // Very slow = likely residential/congested
        }

        return "unclassified";
    }

    private static double getSpeedLimitForRoadType(String roadType) {
        switch (roadType) {
            case "motorway":
                return 100.0;
            case "primary":
                return 80.0;
            case "secondary":
                return 60.0;
            case "tertiary":
                return 50.0;
            case "residential":
                return 30.0;
            case "service":
                return 20.0;
            default:
                return 40.0;
        }
    }

    private static void applyRoadTypeMultipliers(RouteSegment seg) {
        // Acceleration multiplier (harder on motorways, easier on residential)
        switch (seg.roadType) {
            case "motorway":
                seg.accelMultiplier = 1.3;
                seg.emissionMultiplier = 1.15;
                break;
            case "primary":
                seg.accelMultiplier = 1.2;
                seg.emissionMultiplier = 1.1;
                break;
            case "secondary":
                seg.accelMultiplier = 1.0;
                seg.emissionMultiplier = 1.0;
                break;
            case "tertiary":
                seg.accelMultiplier = 0.9;
                seg.emissionMultiplier = 0.95;
                break;
            case "residential":
                seg.accelMultiplier = 0.7;
                seg.emissionMultiplier = 1.05; // More stops = more emissions
                break;
            default:
                seg.accelMultiplier = 1.0;
                seg.emissionMultiplier = 1.0;
        }
    }
}
