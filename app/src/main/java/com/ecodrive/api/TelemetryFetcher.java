package com.ecodrive.api;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelemetryFetcher {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Default open-meteo API endpoint for temperature
    private static final String WEATHER_URL_FORMAT = "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current_weather=true";
    // Default OSRM API endpoint for Routing (OSM) - request full overview geometry
    private static final String ROUTING_URL_FORMAT = "https://router.project-osrm.org/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=polyline&steps=true";
    private static final String NOMINATIM_GEOCODE_URL_FORMAT = "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1";
    private static final String OPEN_METEO_GEOCODE_URL_FORMAT = "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json";
    private static final long NOMINATIM_MIN_INTERVAL_MS = 1200L;
    private static final Object NOMINATIM_LOCK = new Object();
    private static long lastNominatimRequestMs = 0L;
    private static final Map<String, double[]> geocodeCache = new ConcurrentHashMap<>();

    public static void fetchTelemetryAsynchronously(String startLocation, String endLocation,
            TelemetryCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                String effectiveEndLocation = (endLocation == null || endLocation.trim().isEmpty()) ? startLocation : endLocation;

                // 1. Geocode locations (Nominatim or direct coordinate fallback)
                double[] startCoords = geocode(startLocation);
                double[] endCoords = geocode(effectiveEndLocation);

                String startLat = String.valueOf(startCoords[0]);
                String startLon = String.valueOf(startCoords[1]);
                String endLat = String.valueOf(endCoords[0]);
                String endLon = String.valueOf(endCoords[1]);

                // 2. Fetch Weather (current temperature -> Air Density)
                double temperatureC = 25.0;
                double windSpeed = 5.0;
                double humidity = 65.0;
                String condition = "Unknown";
                
                try {
                    String weatherUrl = String.format(WEATHER_URL_FORMAT, endLat, endLon);
                    String weatherJson = httpGet(weatherUrl);
                    JSONObject wObj = new JSONObject(weatherJson);
                    JSONObject current = wObj.getJSONObject("current_weather");
                    temperatureC = current.getDouble("temperature");
                    windSpeed = current.getDouble("windspeed");
                    int weatherCode = current.getInt("weathercode");
                    
                    if (wObj.has("current")) {
                        humidity = wObj.getJSONObject("current").optDouble("relative_humidity_2m", 65.0);
                    }
                    condition = mapWeatherCode(weatherCode);
                } catch (Exception e) {
                    com.ecodrive.utils.AppLog.e("Telemetry", "Weather API failed, using defaults", e);
                    // Continue with default values instead of aborting the entire process
                }

                // 3. Fetch OSRM Routing (Distance and Duration -> Average Speed & Congestion)
                // Note: OSRM uses longitude,latitude format
                
                double distanceKm = 0.0;
                double durationSeconds = 0.0;
                double routeAverageSpeedKmh = 0.0;
                int stops = 0;
                String majorRoadsSummary = "Stationary / Local Area";
                String terrainType = "plains";
                String geometry = "";
                
                // Only query OSRM if there is a significant distance to route
                boolean isSameLocation = Math.abs(startCoords[0] - endCoords[0]) < 0.0001 && Math.abs(startCoords[1] - endCoords[1]) < 0.0001;
                
                if (!isSameLocation) {
                    String routingUrl = String.format(ROUTING_URL_FORMAT, startLon, startLat, endLon, endLat);
                    String routingJson = httpGet(routingUrl);
                    JSONObject rObj = new JSONObject(routingJson);
                    JSONArray routes = rObj.getJSONArray("routes");
                    JSONObject bestRoute = routes.getJSONObject(0);

                    double distanceMeters = bestRoute.getDouble("distance");
                    durationSeconds = bestRoute.getDouble("duration");

                    distanceKm = distanceMeters / 1000.0;
                    routeAverageSpeedKmh = durationSeconds > 0 ? (distanceKm / durationSeconds) * 3600.0 : 0.0;

                    // 4. Calculate Dynamic Stop Ratio from OSRM Steps
                    JSONArray legs = bestRoute.getJSONArray("legs");
                    if (legs.length() > 0) {
                        JSONObject leg = legs.getJSONObject(0);
                        if (leg.has("steps")) {
                            JSONArray steps = leg.getJSONArray("steps");
                            stops = steps.length();
                        }
                    }
                    majorRoadsSummary = extractMajorRoadsSummary(bestRoute);
                    terrainType = classifyTerrainType(startCoords, endCoords, distanceKm);
                    geometry = bestRoute.has("geometry") ? bestRoute.optString("geometry", "") : "";
                }

                double stopRatio = distanceKm > 0 ? Math.min(1.0, (double) stops / (distanceKm * 4.0)) : 0.0;

                final String finalGeometry = geometry;
                final String finalRoads = majorRoadsSummary;
                final String finalTerrain = terrainType;
                final double finalDistanceKm = distanceKm;
                final double finalDuration = durationSeconds;
                final double finalAvgSpeed = routeAverageSpeedKmh;

                final double finalTemp = temperatureC;
                final double fHum = humidity;
                final double fWind = windSpeed;
                final String fCond = condition;

                mainHandler.post(() -> callback.onSuccess(finalDistanceKm, finalDuration, finalTemp, 
                    fHum, fWind, fCond,
                    stopRatio, finalTerrain, finalAvgSpeed, finalGeometry, finalRoads));


            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private static String extractMajorRoadsSummary(JSONObject bestRoute) {
        try {
            Set<String> majorRoads = new LinkedHashSet<>();
            JSONArray legs = bestRoute.optJSONArray("legs");
            if (legs == null || legs.length() == 0) {
                return "Road names unavailable";
            }

            for (int i = 0; i < legs.length(); i++) {
                JSONObject leg = legs.optJSONObject(i);
                if (leg == null) {
                    continue;
                }
                JSONArray steps = leg.optJSONArray("steps");
                if (steps == null) {
                    continue;
                }

                for (int j = 0; j < steps.length(); j++) {
                    JSONObject step = steps.optJSONObject(j);
                    if (step == null) {
                        continue;
                    }

                    String roadName = step.optString("name", "").trim();
                    if (roadName.isEmpty()) {
                        continue;
                    }

                    String lower = roadName.toLowerCase(Locale.US);
                    if (lower.contains("highway") || lower.contains("expressway") || lower.contains("nh")
                            || lower.contains("ring road") || lower.contains("bypass")) {
                        majorRoads.add(roadName);
                    }
                }
            }

            if (majorRoads.isEmpty()) {
                return "No major highway/expressway labels in route steps";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String road : majorRoads) {
                if (count > 0) {
                    sb.append(", ");
                }
                sb.append(road);
                count++;
                if (count >= 8) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "Road extraction failed";
        }
    }

    private static double[] geocode(String location) throws Exception {
        String normalized = location == null ? "" : location.trim();
        if (normalized.isEmpty()) {
            throw new Exception("Location is empty");
        }

        String cacheKey = normalized.toLowerCase(Locale.US);
        double[] cached = geocodeCache.get(cacheKey);
        if (cached != null) {
            return new double[] { cached[0], cached[1] };
        }

        String[] parts = normalized.split(",");
        if (parts.length == 2) {
            try {
                double[] coords = new double[] { Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()) };
                geocodeCache.put(cacheKey, coords);
                return coords;
            } catch (NumberFormatException e) {
                // Fallback to geocoding if it's text with a comma, e.g. "Delhi, India"
            }
        }

        Exception nominatimFailure = null;
        try {
            double[] coords = geocodeWithNominatim(normalized);
            geocodeCache.put(cacheKey, coords);
            return new double[] { coords[0], coords[1] };
        } catch (Exception e) {
            nominatimFailure = e;
            com.ecodrive.utils.AppLog.e("Telemetry", "Nominatim geocoding failed for: " + normalized, e);
        }

        try {
            double[] coords = geocodeWithOpenMeteo(normalized);
            geocodeCache.put(cacheKey, coords);
            return new double[] { coords[0], coords[1] };
        } catch (Exception fallbackError) {
            String base = nominatimFailure != null ? nominatimFailure.getMessage() : "unknown";
            throw new Exception("Geocoding failed for '" + normalized + "'. Nominatim: " + base
                    + " | OpenMeteo: " + fallbackError.getMessage());
        }
    }

    private static double[] geocodeWithNominatim(String location) throws Exception {
        throttleNominatimRequests();
        String urlStr = String.format(Locale.US, NOMINATIM_GEOCODE_URL_FORMAT,
                java.net.URLEncoder.encode(location, "UTF-8"));
        String json = httpGet(urlStr);
        JSONArray arr = new JSONArray(json);
        if (arr.length() > 0) {
            JSONObject obj = arr.getJSONObject(0);
            return new double[] { obj.getDouble("lat"), obj.getDouble("lon") };
        } else {
            throw new Exception("Location not found: " + location);
        }
    }

    private static double[] geocodeWithOpenMeteo(String location) throws Exception {
        String urlStr = String.format(Locale.US, OPEN_METEO_GEOCODE_URL_FORMAT,
                java.net.URLEncoder.encode(location, "UTF-8"));
        String json = httpGet(urlStr);
        JSONObject root = new JSONObject(json);
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new Exception("Location not found: " + location);
        }

        JSONObject best = results.getJSONObject(0);
        return new double[] { best.getDouble("latitude"), best.getDouble("longitude") };
    }

    private static void throttleNominatimRequests() {
        synchronized (NOMINATIM_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = NOMINATIM_MIN_INTERVAL_MS - (now - lastNominatimRequestMs);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastNominatimRequestMs = System.currentTimeMillis();
        }
    }

    private static String classifyTerrainType(double[] startCoords, double[] endCoords, double distanceKm) {
        try {
            String urlStr = String.format(java.util.Locale.US,
                    "https://api.open-elevation.com/api/v1/lookup?locations=%f,%f|%f,%f|%f,%f",
                    startCoords[0], startCoords[1],
                    (startCoords[0] + endCoords[0]) / 2.0, (startCoords[1] + endCoords[1]) / 2.0,
                    endCoords[0], endCoords[1]);

            String json = httpGet(urlStr);
            JSONObject root = new JSONObject(json);
            JSONArray results = root.getJSONArray("results");
            if (results.length() < 2) {
                return "unknown";
            }

            double minElevation = Double.MAX_VALUE;
            double maxElevation = Double.MIN_VALUE;
            for (int i = 0; i < results.length(); i++) {
                double elevation = results.getJSONObject(i).getDouble("elevation");
                minElevation = Math.min(minElevation, elevation);
                maxElevation = Math.max(maxElevation, elevation);
            }

            double elevationRange = Math.max(0.0, maxElevation - minElevation);
            double gradientPerKm = distanceKm > 0 ? elevationRange / distanceKm : elevationRange;

            if (gradientPerKm >= 30.0) {
                return "hilly";
            }
            if (gradientPerKm >= 10.0) {
                return "rolling";
            }
            return "plains";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String httpGet(String urlString) throws Exception {
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "EcoDrive/2.1 (support@ecodrive.app)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int responseCode = conn.getResponseCode();
        java.io.InputStream stream = (responseCode >= 200 && responseCode < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();
        if (stream == null) {
            throw new Exception("HTTP request failed with status " + responseCode + " for " + urlString);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception("HTTP request failed with status " + responseCode + " for " + urlString
                    + " -> " + response.toString());
        }

        return response.toString();
    }

    private static String mapWeatherCode(int code) {
        if (code == 0) return "Clear Sky";
        if (code >= 1 && code <= 3) return "Partly Cloudy";
        if (code >= 45 && code <= 48) return "Foggy";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rainy";
        if (code >= 71 && code <= 77) return "Snowy";
        if (code >= 80 && code <= 82) return "Rain Showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Unknown";
    }
}

