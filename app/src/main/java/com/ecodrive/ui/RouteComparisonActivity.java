package com.ecodrive.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ecodrive.R;
import com.ecodrive.api.RouteComparator;
import com.ecodrive.api.RouteComparator.RouteOption;
import com.ecodrive.data.PlaceLearningManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import com.ecodrive.utils.GeoUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class RouteComparisonActivity extends AppCompatActivity {

    private EditText etStart, etEnd;
    private Button btnCompare;
    private LinearLayout layoutOptions;
    private MapView mapView;
    private List<Polyline> currentOverlays = new ArrayList<>();
    private PlaceLearningManager placeManager;
    private static final Object NOMINATIM_LOCK = new Object();
    private static long lastNominatimRequestMs = 0L;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_comparison);

        placeManager = new PlaceLearningManager(this);
        etStart = findViewById(R.id.etStart);
        etEnd = findViewById(R.id.etEnd);
        btnCompare = findViewById(R.id.btnCompare);
        layoutOptions = findViewById(R.id.layoutOptions);
        mapView = findViewById(R.id.mapViewComparison);

        Configuration.getInstance().load(this, getPreferences(Context.MODE_PRIVATE));
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(12.0);


        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarComparison);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Pre-fill start with most frequent learned place if available
        List<PlaceLearningManager.LearnedPlace> places = placeManager.getLearnedPlaces();
        if (!places.isEmpty()) {
            PlaceLearningManager.LearnedPlace top = places.get(0);
            etStart.setHint("Start: " + top.label + " (" + top.lat + ")");
        }

        btnCompare.setOnClickListener(v -> performComparison());
    }

    private void performComparison() {
        String start = etStart.getText().toString().trim();
        String end = etEnd.getText().toString().trim();

        if (start.isEmpty() || end.isEmpty()) {
            Toast.makeText(this, "Enter start and end locations", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCompare.setEnabled(false);
        btnCompare.setText("Comparing Routes...");

        new Thread(() -> {
            try {
                double[] startCoords = geocode(start);
                double[] endCoords = geocode(end);
                
                RouteComparator.compareRoutes(
                    String.valueOf(startCoords[0]), String.valueOf(startCoords[1]),
                    String.valueOf(endCoords[0]), String.valueOf(endCoords[1]),
                    new RouteComparator.ComparatorCallback() {
                        @Override
                        public void onOptionsFound(List<RouteOption> options) {
                            runOnUiThread(() -> {
                                layoutOptions.removeAllViews();
                                for (Polyline p : currentOverlays) mapView.getOverlays().remove(p);
                                currentOverlays.clear();

                                List<GeoPoint> allPoints = new ArrayList<>();
                                for (int i = 0; i < options.size(); i++) {
                                    RouteOption opt = options.get(i);
                                    addOptionView(opt);
                                    
                                    // Draw on map
                                    if (opt.geometry != null && !opt.geometry.isEmpty()) {
                                        List<GeoPoint> points = GeoUtils.decodePolyline(opt.geometry);
                                        allPoints.addAll(points);
                                        Polyline line = new Polyline();
                                        line.setPoints(points);
                                        // Colors: Green for #1, Blue for others
                                        int color = (opt.rank == 1) ? 0xFF00FF00 : 0xFF4285F4;
                                        line.getOutlinePaint().setColor(color);
                                        line.getOutlinePaint().setStrokeWidth(i == 0 ? 8.0f : 5.0f);
                                        mapView.getOverlays().add(line);
                                        currentOverlays.add(line);
                                    }
                                }

                                if (!allPoints.isEmpty()) {
                                    mapView.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(allPoints), true);
                                }
                                mapView.invalidate();
                                
                                btnCompare.setEnabled(true);
                                btnCompare.setText("Compare Routes");
                            });
                        }


                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(RouteComparisonActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                                btnCompare.setEnabled(true);
                                btnCompare.setText("Compare Routes");
                            });
                        }
                    });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(RouteComparisonActivity.this, "Geocoding failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    btnCompare.setEnabled(true);
                    btnCompare.setText("Compare Routes");
                });
            }
        }).start();
    }

    private double[] geocode(String location) throws Exception {
        String[] parts = location.split(",");
        if (parts.length == 2) {
            try {
                return new double[] { Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()) };
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            throttleNominatimRequests();
            String urlStr = "https://nominatim.openstreetmap.org/search?q=" + java.net.URLEncoder.encode(location, "UTF-8")
                    + "&format=json&limit=1";
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "EcoDrive/2.1 (support@ecodrive.app)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int rc = conn.getResponseCode();
            java.io.InputStream is = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();

            if (rc < 200 || rc >= 300) {
                throw new Exception("HTTP request failed with status " + rc + " for " + urlStr + " -> " + response.toString());
            }

            org.json.JSONArray arr = new org.json.JSONArray(response.toString());
            if (arr.length() > 0) {
                org.json.JSONObject obj = arr.getJSONObject(0);
                return new double[] { obj.getDouble("lat"), obj.getDouble("lon") };
            }
        } catch (Exception nominatimFailure) {
            String fallbackUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + java.net.URLEncoder.encode(location, "UTF-8")
                    + "&count=1&language=en&format=json";
            java.net.URL url = new java.net.URL(fallbackUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "EcoDrive/2.1 (support@ecodrive.app)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int rc = conn.getResponseCode();
            java.io.InputStream is = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            if (rc < 200 || rc >= 300) {
                throw new Exception("Nominatim failed: " + nominatimFailure.getMessage() + " | OpenMeteo failed: "
                        + "HTTP " + rc + " for " + fallbackUrl);
            }
            org.json.JSONObject root = new org.json.JSONObject(response.toString());
            org.json.JSONArray results = root.optJSONArray("results");
            if (results != null && results.length() > 0) {
                org.json.JSONObject obj = results.getJSONObject(0);
                return new double[] { obj.getDouble("latitude"), obj.getDouble("longitude") };
            }
            throw new Exception("Nominatim failed: " + nominatimFailure.getMessage() + " | OpenMeteo returned no results");
        }

        throw new Exception("Location not found: " + location);
    }

    private static void throttleNominatimRequests() {
        synchronized (NOMINATIM_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = 1200L - (now - lastNominatimRequestMs);
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

    private void addOptionView(RouteOption opt) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_route_option, null);
        TextView tvName = view.findViewById(R.id.tvRouteName);
        TextView tvMetrics = view.findViewById(R.id.tvRouteMetrics);
        Button btnSelect = view.findViewById(R.id.btnSelectRoute);

        String rankLabel = opt.rank == 1 ? " ★ Greenest" : "";
        tvName.setText(opt.name + " (Rank #" + opt.rank + ")" + rankLabel);
        tvMetrics.setText(String.format(Locale.US,
                "%.2f km | %.1f min | %.1f km/h\nEst. %.3f kg CO2 | %.2f L fuel",
                opt.distanceKm, opt.durationSec / 60.0, opt.avgSpeedKmh,
                opt.estimatedCo2Kg, opt.estimatedFuelL));

        btnSelect.setOnClickListener(v -> {
            // Only return compact payload when this screen was launched for result.
            // Large polyline geometry can cause TransactionTooLarge crashes on some devices.
            if (getCallingActivity() != null) {
                android.content.Intent resultIntent = new android.content.Intent();
                resultIntent.putExtra("selected_distance_km", opt.distanceKm);
                resultIntent.putExtra("selected_duration_sec", opt.durationSec);
                resultIntent.putExtra("selected_co2_kg", opt.estimatedCo2Kg);
                setResult(RESULT_OK, resultIntent);
            }
            Toast.makeText(this, "Route " + opt.rank + " selected: " + 
                    String.format(Locale.US, "%.2f km, %.3f kg CO2", opt.distanceKm, opt.estimatedCo2Kg),
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        layoutOptions.addView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}

