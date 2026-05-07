package com.ecodrive.api;

import com.ecodrive.ml.EmissionsMLModel;
import com.ecodrive.ml.TripTelemetry;
import com.ecodrive.physics.DynamicVehicleEmissions;
import com.ecodrive.physics.PhysicsFeatureSet;
import com.ecodrive.physics.TripResult;
import com.ecodrive.physics.VehicleSpecs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches multiple route alternatives from OSRM and ranks them by CO2 efficiency
 * using the full Physics + ML fusion pipeline (not a flat proxy).
 */
public class RouteComparator {

    private static final String[] ROUTING_ENDPOINTS = new String[] {
            "https://router.project-osrm.org/route/v1/driving/%s,%s;%s,%s?alternatives=3&overview=full&geometries=polyline&steps=true",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving/%s,%s;%s,%s?alternatives=3&overview=full&geometries=polyline&steps=true"
    };

    public static class RouteOption {
        public String name;
        public double distanceKm;
        public double durationSec;
        public String geometry;
        public double estimatedCo2Kg;
        public double estimatedFuelL;
        public double avgSpeedKmh;
        public int rank;

        public RouteOption(String name, double dist, double dur, String geom) {
            this.name = name;
            this.distanceKm = dist;
            this.durationSec = dur;
            this.geometry = geom;
            this.avgSpeedKmh = dur > 0 ? (dist / dur) * 3600.0 : 0;
        }
    }

    public interface ComparatorCallback {
        void onOptionsFound(List<RouteOption> options);
        void onError(String error);
    }

    /**
     * Compare routes using the real physics model for CO2 estimation.
     * @param vehicle The selected vehicle (null = use default sedan)
     * @param fuelType The selected fuel type (null = PETROL)
     * @param temperatureC Current temperature for air density calculation
     * @param acOn Whether AC is on
     */
    public static void compareRoutes(String startLat, String startLng, String endLat, String endLng,
                                     VehicleSpecs vehicle, VehicleSpecs.FuelType fuelType,
                                     double temperatureC, boolean acOn,
                                     ComparatorCallback callback) {
        new Thread(() -> {
            try {
                JSONArray routes = fetchRoutesFromAvailableEndpoints(startLat, startLng, endLat, endLng);
                List<RouteOption> options = new ArrayList<>();

                // Use default vehicle if none provided
                VehicleSpecs v = vehicle != null ? vehicle
                        : new VehicleSpecs("Default Sedan", 1200.0, 1200.0, 2.2, 0.32,
                                VehicleSpecs.Category.SEDAN, VehicleSpecs.FuelType.PETROL);
                VehicleSpecs.FuelType ft = fuelType != null ? fuelType : v.fuelType;
                v.fuelType = ft;
                v.isEV = (ft == VehicleSpecs.FuelType.EV);

                EmissionsMLModel mlModel = new EmissionsMLModel();
                double airDensity = 101325.0 / (287.05 * (temperatureC + 273.15));

                for (int i = 0; i < routes.length(); i++) {
                    JSONObject route = routes.getJSONObject(i);
                    double dist = route.getDouble("distance") / 1000.0;
                    double dur = route.getDouble("duration");
                    String geom = route.optString("geometry", "");
                    RouteOption opt = new RouteOption("Route " + (i + 1), dist, dur, geom);

                    // Calculate stop ratio from OSRM steps
                    double stopRatio = 0.0;
                    JSONArray legs = route.optJSONArray("legs");
                    if (legs != null && legs.length() > 0) {
                        JSONObject leg = legs.getJSONObject(0);
                        JSONArray steps = leg.optJSONArray("steps");
                        int stepCount = steps != null ? steps.length() : 0;
                        stopRatio = dist > 0 ? Math.min(1.0, (double) stepCount / (dist * 4.0)) : 0.0;
                    }

                    // Run the REAL physics + ML pipeline (1000 iterations for speed)
                    double avgSpeedKmh = opt.avgSpeedKmh > 0 ? opt.avgSpeedKmh : 40.0;
                    double accelEstimate = 0.5; // Moderate driving assumption

                    List<PhysicsFeatureSet> features = DynamicVehicleEmissions.generatePhysicsFeatures(
                            v, dist, avgSpeedKmh, accelEstimate, 0.0, airDensity, 1000);

                    TripTelemetry telemetry = new TripTelemetry(
                            accelEstimate * 0.45, stopRatio, acOn, System.currentTimeMillis(),
                            "unknown", avgSpeedKmh);

                    TripResult result = mlModel.predictTripEmissions(features, telemetry, true);
                    opt.estimatedCo2Kg = result.meanCo2Kg;
                    opt.estimatedFuelL = result.meanFuelLitres;

                    options.add(opt);
                }

                // Rank by CO2 (lowest first)
                options.sort((a, b) -> Double.compare(a.estimatedCo2Kg, b.estimatedCo2Kg));
                for (int i = 0; i < options.size(); i++) {
                    options.get(i).rank = i + 1;
                }

                callback.onOptionsFound(options);

            } catch (Exception e) {
                // FALLBACK: If OSRM crashes/timeouts on long Indian routes, calculate mathematical Euclidean path
                try {
                    double lat1 = Double.parseDouble(startLat);
                    double lon1 = Double.parseDouble(startLng);
                    double lat2 = Double.parseDouble(endLat);
                    double lon2 = Double.parseDouble(endLng);
                    
                    // Haversine distance
                    double R = 6371; // Radius of the earth in km
                    double dLat = Math.toRadians(lat2 - lat1);
                    double dLon = Math.toRadians(lon2 - lon1);
                    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                               Math.sin(dLon / 2) * Math.sin(dLon / 2);
                    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                    double euclideanDistance = R * c;
                    
                    // Highway curvature multiplier for realism
                    double fallbackDist = euclideanDistance * 1.35; 
                    double fallbackDur = (fallbackDist / 60.0) * 3600.0; // Assume 60km/h average
                    
                    List<RouteOption> fallbackOptions = new ArrayList<>();
                    RouteOption fallbackOpt = new RouteOption("Direct Route (AI Fallback)", fallbackDist, fallbackDur, "");
                    fallbackOpt.rank = 1;
                    
                    VehicleSpecs v = vehicle != null ? vehicle
                            : new VehicleSpecs("Default Sedan", 1200.0, 1200.0, 2.2, 0.32,
                                    VehicleSpecs.Category.SEDAN, VehicleSpecs.FuelType.PETROL);
                    VehicleSpecs.FuelType ft = fuelType != null ? fuelType : v.fuelType;
                    v.fuelType = ft;
                    v.isEV = (ft == VehicleSpecs.FuelType.EV);
                    
                    EmissionsMLModel mlModel = new EmissionsMLModel();
                    double airDensity = 101325.0 / (287.05 * (temperatureC + 273.15));
                    
                    List<PhysicsFeatureSet> features = DynamicVehicleEmissions.generatePhysicsFeatures(
                            v, fallbackDist, 60.0, 0.5, 0.0, airDensity, 1000);
                    TripTelemetry telemetry = new TripTelemetry(
                            0.5 * 0.45, 0.0, acOn, System.currentTimeMillis(),
                            "unknown", 60.0);
                    TripResult result = mlModel.predictTripEmissions(features, telemetry, true);
                    fallbackOpt.estimatedCo2Kg = result.meanCo2Kg;
                    fallbackOpt.estimatedFuelL = result.meanFuelLitres;
                    
                    fallbackOptions.add(fallbackOpt);
                    callback.onOptionsFound(fallbackOptions);
                } catch (Exception ex) {
                    callback.onError("OSRM routing failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private static JSONArray fetchRoutesFromAvailableEndpoints(String startLat, String startLng,
                                                              String endLat, String endLng) throws Exception {
        Exception lastError = null;
        for (String endpointTemplate : ROUTING_ENDPOINTS) {
            try {
                String urlStr = String.format(Locale.US, endpointTemplate, startLng, startLat, endLng, endLat);
                String json = httpGet(urlStr);
                JSONObject root = new JSONObject(json);
                JSONArray routes = root.optJSONArray("routes");
                if (routes != null && routes.length() > 0) {
                    return routes;
                }
                lastError = new Exception("No routes returned by endpoint: " + urlStr);
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new Exception("All routing endpoints failed", lastError);
    }

    private static String httpGet(String urlString) throws Exception {
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "EcoDrive/2.1 (support@ecodrive.app)");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

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

    /**
     * Backward-compatible version without vehicle context (uses default sedan).
     */
    public static void compareRoutes(String startLat, String startLng, String endLat, String endLng,
                                   ComparatorCallback callback) {
        compareRoutes(startLat, startLng, endLat, endLng, null, null, 25.0, false, callback);
    }
}
