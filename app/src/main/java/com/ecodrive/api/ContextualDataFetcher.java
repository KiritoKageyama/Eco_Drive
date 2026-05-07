package com.ecodrive.api;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContextualDataFetcher {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ContextCallback {
        void onContextUpdated(String roadType, int trafficSignalCount);
    }

    /**
     * Periodically called by the LocationTracker (e.g. every 500m shifted)
     * Calls Overpass API to get surrounding highway= keys
     */
    public static void fetchLocalContext(double lat, double lon, ContextCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            try {
                // Short-range bounding box (approx 200m radius)
                double offset = 0.002;
                String query = String.format(java.util.Locale.US,
                        "[out:json];" +
                                "(way[%22highway%22](%f,%f,%f,%f);" +
                    "way[%22landuse%22](%f,%f,%f,%f);" +
                    "way[%22natural%22](%f,%f,%f,%f);" +
                                "node[%22highway%22=%22traffic_signals%22](%f,%f,%f,%f););" +
                                "out body;",
                        lat - offset, lon - offset, lat + offset, lon + offset,
                lat - offset, lon - offset, lat + offset, lon + offset,
                lat - offset, lon - offset, lat + offset, lon + offset,
                        lat - offset, lon - offset, lat + offset, lon + offset);

                String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
                String urlStr = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;

                String jsonResponse = httpGet(urlStr);
                JSONObject root = new JSONObject(jsonResponse);
                JSONArray elements = root.getJSONArray("elements");

                String roadType = "unknown";
                int signalCount = 0;

                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);
                    if (element.has("tags")) {
                        JSONObject tags = element.getJSONObject("tags");
                        if (tags.has("highway")) {
                            String type = tags.getString("highway");
                            if (type.equals("traffic_signals")) {
                                signalCount++;
                            } else if (roadType.equals("unknown")) {
                                // Assign the first major road type found in the area
                                roadType = type;
                            }
                        } else if (roadType.equals("unknown") && tags.has("landuse")) {
                            String type = tags.getString("landuse");
                            if (type.equals("residential") || type.equals("commercial") || type.equals("industrial")
                                    || type.equals("farmland") || type.equals("forest") || type.equals("grass")) {
                                roadType = type;
                            }
                        } else if (roadType.equals("unknown") && tags.has("natural")) {
                            String type = tags.getString("natural");
                            if (type.equals("wood") || type.equals("scrub") || type.equals("water")
                                    || type.equals("hill")) {
                                roadType = type;
                            }
                        }
                    }
                }

                String finalRoadType = roadType;
                int finalSignalCount = signalCount;
                mainHandler.post(() -> callback.onContextUpdated(finalRoadType, finalSignalCount));
            } catch (Exception e) {
                // Ignore context errors and retain old context
                e.printStackTrace();
            }
        });
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        // Required for OSM Nominatim / Overpass APIs
        conn.setRequestProperty("User-Agent", "EcoDriveApp/1.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }
}
