package com.ecodrive.ui;

import android.content.Intent;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import com.ecodrive.R;
import com.ecodrive.data.VehicleDatabase;
import com.ecodrive.data.PlaceLearningManager;
import com.ecodrive.ml.OnDeviceLLMManager;
import com.ecodrive.ml.EmissionsMLModel;
import com.ecodrive.ml.TripAIAssistant;
import com.ecodrive.ml.TripTelemetry;
import com.ecodrive.physics.DynamicVehicleEmissions;
import com.ecodrive.physics.PhysicsFeatureSet;
import com.ecodrive.physics.TripResult;
import com.ecodrive.physics.VehicleSpecs;
import com.ecodrive.reports.ReportGenerator;
import com.ecodrive.tracking.LocationTracker;
import com.ecodrive.utils.GeoUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private android.widget.AutoCompleteTextView spinnerCategory, spinnerFuelType;
    private EditText etDistance, etSpeed, etStopRatio, etAccel, etGrade;
    private EditText etEndLoc;
    private TextView tvCurrentLocation, tvAddDestination;
    private View layoutDestination;
    private Button btnFetchTelemetry;
    private SwitchMaterial switchAc;

    // Weather Telemetry UI
    private TextView tvMainTemp, tvWeatherCondition, tvWindSpeed, tvHumidity, tvTrafficCondition, tvRoadArea, tvWeatherStatus;

    private Button btnCalculate;
    private MaterialButton btnDemoEco, btnDemoAggressive, btnDemoTraffic;
    private TextView tvResults;

    private BarChart barChartEmissions;
    private PieChart pieChartEnergy;

    // Explainability Panel Views
    private TextView tvCalcHeader, tvCalcDetails;
    private TextView tvAiHeader, tvAiDetails;

    private FusedLocationProviderClient fusedLocationClient;

    private MaterialButton btnToggleTracking;
    private TextView tvLiveMetrics;
    private LocationTracker locationTracker;
    private OnDeviceLLMManager llmManager;
    private PlaceLearningManager placeManager;
    private boolean isLiveTracking = false;
    private double cumulativeDistanceKm = 0.0;
    private double cumulativeCo2Kg = 0.0;
    private double cumulativeBaseCo2Kg = 0.0;
    private long tripStartTime = 0;
    private double idleTimeSec = 0.0;
    private String currentTerrainType = "unknown";
    private double currentRouteAverageSpeedKmh = 0.0;
    private String currentMajorRoadsSummary = "No major roads detected";

    private MapView mapView; // OSM Interactive Map Instance
    private Polyline currentRouteOverlay;
    private org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay myLocationOverlay;
    private TextView tvLiveSpeed;
    private TextView tvLiveCo2;
    private View cardLiveHud;
    private long lastHudUpdate = 0;
    private long lastAiThoughtUpdate = 0;
    private TextView tvAiThoughtStream;
    private android.widget.ProgressBar pbAiThinking;
    private View cardLiveWeatherRoute;

    private final EmissionsMLModel mlModel = new EmissionsMLModel();
    private double currentTemperatureC = 25.0; // Default until API updates
    private long lastWeatherUpdate = 0;

    private final android.content.BroadcastReceiver locationReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if ("com.ecodrive.LOCATION_UPDATE".equals(intent.getAction())) {
                double lat = intent.getDoubleExtra("lat", 0);
                double lon = intent.getDoubleExtra("lon", 0);
                double speed = intent.getDoubleExtra("speed", 0);
                double distanceDelta = intent.getDoubleExtra("distanceDelta", 0);
                
                android.location.Location loc = new android.location.Location("service");
                loc.setLatitude(lat);
                loc.setLongitude(lon);
                
                // Track location updates manually in MainActivity context for UI
                updateLiveMetrics(distanceDelta, speed, locationTracker.getContextEngine());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Critical: Initialize OSM configuration BEFORE inflating layout
        Configuration.getInstance().load(getApplicationContext(),
                getApplicationContext().getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("EcoDrive/2.1 (support@ecodrive.app)");

        setContentView(R.layout.activity_main);

        // Init views
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerFuelType = findViewById(R.id.spinnerFuelType);
        etDistance = findViewById(R.id.etDistance);
        etSpeed = findViewById(R.id.etSpeed);
        etStopRatio = findViewById(R.id.etStopRatio);
        etAccel = findViewById(R.id.etAccel);
        etGrade = findViewById(R.id.etGrade);
        switchAc = findViewById(R.id.switchAc);
        btnCalculate = findViewById(R.id.btnCalculate);
        btnDemoEco = findViewById(R.id.btnDemoEco);
        btnDemoAggressive = findViewById(R.id.btnDemoAggressive);
        btnDemoTraffic = findViewById(R.id.btnDemoTraffic);
        tvResults = findViewById(R.id.tvResults);

        barChartEmissions = findViewById(R.id.barChartEmissions);
        pieChartEnergy = findViewById(R.id.pieChartEnergy);

        // Explainability Panel Setup
        tvCalcHeader = findViewById(R.id.tvCalcHeader);
        tvCalcDetails = findViewById(R.id.tvCalcDetails);
        tvAiHeader = findViewById(R.id.tvAiHeader);
        tvAiDetails = findViewById(R.id.tvAiDetails);

        if (tvCalcHeader != null) {
            tvCalcHeader.setOnClickListener(v -> toggleVisibility(tvCalcDetails));
            tvAiHeader.setOnClickListener(v -> toggleVisibility(tvAiDetails));
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        barChartEmissions.getDescription().setEnabled(false);
        pieChartEnergy.getDescription().setEnabled(false);

        // Hide charts initially
        barChartEmissions.setVisibility(View.GONE);
        pieChartEnergy.setVisibility(View.GONE);

        tvCurrentLocation = findViewById(R.id.tvCurrentLocation);
        tvAddDestination = findViewById(R.id.tvAddDestination);
        layoutDestination = findViewById(R.id.layoutDestination);
        etEndLoc = findViewById(R.id.etEndLoc);
        btnFetchTelemetry = findViewById(R.id.btnFetchTelemetry);

        tvAddDestination.setOnClickListener(v -> {
            boolean isVisible = layoutDestination.getVisibility() == View.VISIBLE;
            layoutDestination.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            tvAddDestination.setText(isVisible ? "+ Add Destination (Optional)" : "- Hide Destination");
        });

        btnToggleTracking = findViewById(R.id.btnToggleTracking);
        tvLiveMetrics = findViewById(R.id.tvLiveMetrics);

        llmManager = new OnDeviceLLMManager(this);
        placeManager = new PlaceLearningManager(this);

        // Top Navigation wiring
        findViewById(R.id.btnOpenComparison).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, RouteComparisonActivity.class)));
        findViewById(R.id.btnOpenDynamicTest).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, DynamicTestActivity.class)));
        findViewById(R.id.btnOpenDashboard).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, DashboardActivity.class)));
        findViewById(R.id.btnOpenSensitivity).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, SensitivityAnalysisActivity.class)));
        findViewById(R.id.btnOpenChat).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, AIChatActivity.class)));
        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class)));

        locationTracker = new LocationTracker(this, (location, distanceDeltaKm, speedKmh, contextEngine) -> {
            updateLiveMetrics(distanceDeltaKm, speedKmh, contextEngine);
        });

        locationTracker.setAutoEndListener(() -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "Trip Auto-Ended (1 Hour Idle Limit Reached). Saving to DB...", Toast.LENGTH_LONG)
                        .show();
                toggleLiveTracking(); // Automatically disable UI states and save trip
            });
        });

        // Add stop detection listener (5+ minute stops generate intermediate reports)
        locationTracker.setStopDetectionListener(new com.ecodrive.tracking.LocationTracker.StopDetectionListener() {
            @Override
            public void onLongStopDetected(android.location.Location location, double totalDistanceKm, double totalEmissionKg) {
                runOnUiThread(() -> {
                    String message = String.format("⏸️ Long Stop Detected!\nDistance: %.2f km | CO2: %.2f kg", 
                        totalDistanceKm, totalEmissionKg);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    
                    // Generate intermediate report
                    String intermediateReport = String.format(
                        "📍 INTERMEDIATE TRIP REPORT\n" +
                        "Location: %.2f°, %.2f°\n" +
                        "Distance (before stop): %.2f km\n" +
                        "CO2 Emitted: %.2f kg\n" +
                        "Tracking paused. Tap 'Resume' when ready.",
                        location.getLatitude(), location.getLongitude(),
                        totalDistanceKm, totalEmissionKg);
                    
                    tvResults.setText(intermediateReport);
                    tvResults.setVisibility(android.view.View.VISIBLE);
                });
            }

            @Override
            public void onResumeAfterStop() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "▶️ Tracking Resumed", Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Initialize Native OSM Map
        mapView = findViewById(R.id.mapView);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18.0);
        // Default viewport on Delhi
        mapView.getController().setCenter(new GeoPoint(28.6139, 77.2090));

        // Add Live Location Overlay
        myLocationOverlay = new org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(new org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        
        // Use custom car icon
        android.graphics.drawable.Drawable carDrawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_car_nav);
        if (carDrawable != null) {
            android.graphics.Bitmap carBitmap = com.ecodrive.utils.GeoUtils.drawableToBitmap(carDrawable);
            // Scale it to be about 48dp (roughly double standard route width)
            int sizePx = (int) (48 * getResources().getDisplayMetrics().density);
            android.graphics.Bitmap scaledCar = android.graphics.Bitmap.createScaledBitmap(carBitmap, sizePx, sizePx, true);
            myLocationOverlay.setPersonIcon(scaledCar);
            myLocationOverlay.setDirectionArrow(scaledCar, scaledCar);
        }
        
        mapView.getOverlays().add(myLocationOverlay);


        // HUD Initialization
        cardLiveHud = findViewById(R.id.cardLiveHud);
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed);
        tvLiveCo2 = findViewById(R.id.tvLiveCo2);

        // Weather Telemetry Initialization
        tvMainTemp = findViewById(R.id.tvMainTemp);
        tvWeatherCondition = findViewById(R.id.tvWeatherCondition);
        tvWindSpeed = findViewById(R.id.tvWindSpeed);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvTrafficCondition = findViewById(R.id.tvTrafficCondition);
        tvRoadArea = findViewById(R.id.tvRoadArea);
        tvWeatherStatus = findViewById(R.id.tvWeatherStatus);
        tvAiThoughtStream = findViewById(R.id.tvAiThoughtStream);
        pbAiThinking = findViewById(R.id.pbAiThinking);
        cardLiveWeatherRoute = findViewById(R.id.cardLiveWeatherRoute);

        // Auto-fetch when destination is entered (on focus lost)
        etEndLoc.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !etEndLoc.getText().toString().trim().isEmpty()) {
                fetchLiveTelemetry(false);
            }
        });


        // 1. Setup Data-driven Car selection for the modular pipeline
        VehicleDatabase.init(this);
        List<VehicleSpecs> allVehicles = VehicleDatabase.getAllVehicles();
        spinnerCategory.setAdapter(new ArrayAdapter<>(this,
                R.layout.item_dropdown, allVehicles));

        // Wire dynamic fuel constraints
        spinnerCategory.setOnItemClickListener((parent, view, position, id) -> {
            VehicleSpecs selected = (VehicleSpecs) parent.getItemAtPosition(position);
            spinnerFuelType.setAdapter(new ArrayAdapter<>(MainActivity.this,
                    R.layout.item_dropdown, selected.allowedFuelTypes));

            if (selected.allowedFuelTypes != null && selected.allowedFuelTypes.size() > 0) {
                spinnerFuelType.setText(selected.allowedFuelTypes.get(0).name(), false);
            }
        });

        if (!allVehicles.isEmpty()) {
            spinnerCategory.setText(allVehicles.get(0).toString(), false);
            spinnerFuelType.setAdapter(new ArrayAdapter<>(MainActivity.this,
                    R.layout.item_dropdown, allVehicles.get(0).allowedFuelTypes));
            spinnerFuelType.setText(allVehicles.get(0).allowedFuelTypes.get(0).name(), false);
        }

        btnCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ✅ Move physics calculation to background thread to prevent UI freeze
                btnCalculate.setEnabled(false);
                btnCalculate.setText("Calculating...");

                new Thread(() -> {
                    try {
                        calculateAndDisplayModularInternal(true, false);
                        runOnUiThread(() -> {
                            btnCalculate.setEnabled(true);
                            btnCalculate.setText("Calculate Emissions");
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            btnCalculate.setEnabled(true);
                            btnCalculate.setText("Calculate Emissions");
                        });
                    }
                }).start();
            }
        });

        // Demo Scenario Listeners
        btnDemoEco.setOnClickListener(v -> {
            applyDemoScenario(50.0, 60.0, 0.05, 0.4, 0.0, false);
            Toast.makeText(this, "🌱 Eco Route Applied & Saved!", Toast.LENGTH_SHORT).show();
        });

        btnDemoAggressive.setOnClickListener(v -> {
            applyDemoScenario(15.0, 80.0, 0.3, 3.5, 2.0, true);
            Toast.makeText(this, "🏎️ Aggressive Driver Applied & Saved!", Toast.LENGTH_SHORT).show();
        });

        btnDemoTraffic.setOnClickListener(v -> {
            applyDemoScenario(5.0, 15.0, 0.8, 1.2, 0.0, true);
            Toast.makeText(this, "🚦 Heavy Traffic Applied & Saved!", Toast.LENGTH_SHORT).show();
        });

        btnFetchTelemetry.setOnClickListener(v -> fetchLiveTelemetry(false));

        btnToggleTracking.setOnClickListener(v -> toggleLiveTracking());

        // Register receiver
        android.content.IntentFilter filter = new android.content.IntentFilter("com.ecodrive.LOCATION_UPDATE");
        registerReceiver(locationReceiver, filter, android.content.Context.RECEIVER_EXPORTED);

        // Auto-fetch initial telemetry/weather silently after 2 seconds to avoid UI block
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> fetchLiveTelemetry(true), 2000);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null)
            mapView.onResume();

        // Trigger initial location fetch if we don't have it
        if (tvCurrentLocation != null && tvCurrentLocation.getText().toString().equals("Fetching GPS...")) {
            fetchInitialLocation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null)
            mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(locationReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void toggleLiveTracking() {
        if (!com.ecodrive.tracking.EcoDriveTrackingService.isRunning) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION },
                        1002);
                return;
            }

            cumulativeDistanceKm = 0.0;
            cumulativeCo2Kg = 0.0;
            cumulativeBaseCo2Kg = 0.0;
            idleTimeSec = 0.0;
            tripStartTime = System.currentTimeMillis();
            tvResults.setText("Live Trip Started (Foreground Mode)...");
            tvLiveMetrics.setText("Acquiring GPS signal via Service...");
            btnToggleTracking.setText("Stop Live Trip");
            btnCalculate.setEnabled(false);

            android.content.Intent serviceIntent = new android.content.Intent(this, com.ecodrive.tracking.EcoDriveTrackingService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            isLiveTracking = true;
        } else {
            android.content.Intent serviceIntent = new android.content.Intent(this, com.ecodrive.tracking.EcoDriveTrackingService.class);
            stopService(serviceIntent);
            
            btnToggleTracking.setText("Start Live Trip");
            btnCalculate.setEnabled(true);
            isLiveTracking = false;

            saveTripToDatabase();
        }
    }

    private void saveTripToDatabase() {
        if (cumulativeDistanceKm <= 0.01)
            return; // Prevent saving bogus empty trips

        long endMs = System.currentTimeMillis();
        String origin = tvCurrentLocation.getText().toString();
        String dest = layoutDestination.getVisibility() == View.VISIBLE ? etEndLoc.getText().toString() : "Free Drive";

        com.ecodrive.data.TripEntity trip = new com.ecodrive.data.TripEntity(
                endMs,
                cumulativeDistanceKm,
                cumulativeCo2Kg,
                cumulativeBaseCo2Kg,
                idleTimeSec,
                origin,
                dest);

        new Thread(() -> {
            com.ecodrive.data.EcoDatabase.getDatabase(getApplicationContext()).tripDao().insertTrip(trip);
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Trip successfully saved to Local Dashboard Database.", Toast.LENGTH_SHORT).show());
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fetchInitialLocation();
        } else if (requestCode == 1002 && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            toggleLiveTracking();
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchInitialLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION },
                    1001);
            return;
        }

        locationTracker.fetchSingleLocation(location -> {
            if (location != null) {
                // Reverse geocode explicitly to get the city name
                new Thread(() -> {
                    try {
                                String reverseUrl = "https://nominatim.openstreetmap.org/reverse?format=json&lat="
                                        + location.getLatitude() + "&lon=" + location.getLongitude() + "&zoom=10";
                                java.net.URL url = new java.net.URL(reverseUrl);
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setRequestProperty("User-Agent", "EcoDrive/2.0 (eco@example.com)");
                                conn.setConnectTimeout(5000);
                                conn.setReadTimeout(5000);

                                int rc = conn.getResponseCode();
                                java.io.InputStream is = (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream();
                                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                                StringBuilder response = new StringBuilder();
                                String inputLine;
                                while ((inputLine = in.readLine()) != null) {
                                    response.append(inputLine);
                                }
                                in.close();

                                if (rc < 200 || rc >= 300) {
                                    throw new Exception("HTTP request failed with status " + rc + " for " + reverseUrl + " -> " + response.toString());
                                }

                                org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
                                String displayName = jsonResponse.optString("display_name", "Unknown Location");
                        // Extract just the city or first major part
                        String[] parts = displayName.split(",");
                        final String displayCity = parts.length > 0 ? parts[0].trim() : "Current Location";

                        runOnUiThread(() -> tvCurrentLocation.setText(displayCity));

                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> tvCurrentLocation
                                .setText(location.getLatitude() + ", " + location.getLongitude()));
                    }
                }).start();

                // Pan map to user's real-time position
                mapView.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));

            } else {
                tvCurrentLocation.setText("Location Unavailable");
            }
        });
    }

    private VehicleSpecs getSelectedVehicle() {
        String name = spinnerCategory.getText().toString();
        for (VehicleSpecs v : VehicleDatabase.getAllVehicles()) {
            if (v.toString().equals(name))
                return v;
        }
        return VehicleDatabase.getAllVehicles().get(0);
    }

    private VehicleSpecs.FuelType getSelectedFuelType() {
        String fuelName = spinnerFuelType.getText().toString();
        for (VehicleSpecs.FuelType f : VehicleSpecs.FuelType.values()) {
            if (f.name().equals(fuelName))
                return f;
        }
        return getSelectedVehicle().allowedFuelTypes.get(0);
    }

    private void updateLiveMetrics(double distanceDeltaKm, double speedKmh,
            com.ecodrive.tracking.TripContextEngine context) {
        cumulativeDistanceKm += distanceDeltaKm;

        // Try to center map on current location if available
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(myLocationOverlay.getMyLocation());
        }

        VehicleSpecs selectedVehicle = getSelectedVehicle();
        VehicleSpecs.FuelType fuelOverride = getSelectedFuelType();
        selectedVehicle.fuelType = fuelOverride;
        selectedVehicle.isEV = (fuelOverride == VehicleSpecs.FuelType.EV);

        double dynamicAirDensity = 101325.0 / (287.05 * (currentTemperatureC + 273.15));
        double terrainGradePercent = Math.max(context.getDynamicRoadGradePercent(), GeoUtils.estimateTerrainGradeFromLabel(context.getCurrentTerrainType()));

        // Predictive Engine-Off saves fuel if idling constantly
        if (!context.isEngineOff()) {
            if (distanceDeltaKm > 0 || speedKmh < 2.0) {
                // Determine stop ratio via deep open-street map nodes if moving
                double preciseStopRatio = context.getDynamicStopRatio();

                // Process the physics using only 1 iteration for realtime speed
                List<PhysicsFeatureSet> physicsFeatures = DynamicVehicleEmissions.generatePhysicsFeatures(
                selectedVehicle, Math.max(distanceDeltaKm, 0.001), speedKmh, terrainGradePercent, preciseStopRatio,
                        dynamicAirDensity, 1);

                TripTelemetry telemetry = new TripTelemetry(
                0.0, preciseStopRatio, switchAc.isChecked(), System.currentTimeMillis(),
                context.getCurrentTerrainType(), currentRouteAverageSpeedKmh > 0 ? currentRouteAverageSpeedKmh : speedKmh);

                TripResult tickResult = mlModel.predictTripEmissions(physicsFeatures, telemetry, false);
                cumulativeCo2Kg += tickResult.meanCo2Kg;
                cumulativeBaseCo2Kg += physicsFeatures.get(0).baseCo2Kg;

                // Update Floating HUD
                long now = System.currentTimeMillis();
                if (now - lastHudUpdate > 500) { // Update HUD twice a second max to avoid flicker
                    final double liveCo2Rate = (tickResult.meanCo2Kg * 1000.0); // Rough estimation of g/sec per tick
                    runOnUiThread(() -> {
                        if (cardLiveHud != null && cardLiveHud.getVisibility() != View.VISIBLE) {
                            cardLiveHud.setVisibility(View.VISIBLE);
                        }
                        if (tvLiveSpeed != null) tvLiveSpeed.setText(String.format(java.util.Locale.US, "%.0f", speedKmh));
                        if (tvLiveCo2 != null) tvLiveCo2.setText(String.format(java.util.Locale.US, "%.1f", liveCo2Rate));
                    });
                    lastHudUpdate = now;
                }

            }
        } else {
            // Engine OFF / Idling completely without moving
            idleTimeSec += 2.0; // Approximation of tick interval
        }

        String metrics = String.format(java.util.Locale.US,
            "Speed: %.1f km/h | State: %s\nArea: %s | Terrain: %s\nDistance: %.2f km\nCumulative CO2: %.3f kg",
            speedKmh, context.getCurrentState().name(), context.getCurrentAreaType(),
            context.getCurrentTerrainType(), cumulativeDistanceKm, cumulativeCo2Kg);

        tvLiveMetrics.setText(metrics);
        TextView tvSpeedometer = findViewById(R.id.tvSpeedometer);
        if (tvSpeedometer != null) {
            tvSpeedometer.setText(String.format(java.util.Locale.US, "%.0f\nkm/h", speedKmh));
        }
        tvResults.setText(String.format(java.util.Locale.US,
                "LIVE TRACKING ACTIVE\n\nTotal Distance: %.2f km\nTotal Est CO2: %.3f kg\nEngine Status: %s",
                cumulativeDistanceKm, cumulativeCo2Kg, context.isEngineOff() ? "AUTO-OFF" : "RUNNING"));

        // Periodically update AI Thought Stream (every 45 seconds during live trip)
        long now = System.currentTimeMillis();
        if (now - lastAiThoughtUpdate > 45000) {
            updateAiThoughtStream(speedKmh, context);
            lastAiThoughtUpdate = now;
        }

        // Periodically fetch live Weather & Telemetry (every 5 minutes) during live trip
        if (now - lastWeatherUpdate > 300000 && isLiveTracking) {
            fetchLiveTelemetry(true);
            lastWeatherUpdate = now;
        }
    }

    private void updateAiThoughtStream(double currentSpeed, com.ecodrive.tracking.TripContextEngine context) {
        if (pbAiThinking != null) pbAiThinking.setVisibility(View.VISIBLE);
        
        String prompt = String.format(java.util.Locale.US,
            "You are EcoDrive AI. Analyze current telemetry: Speed %.1f km/h, Terrain: %s, Area: %s. " +
            "Give a 1-sentence proactive eco-tip based on this context. Be concise.",
            currentSpeed, context.getCurrentTerrainType(), context.getCurrentAreaType());

        com.ecodrive.api.DualLLMRouter.queryDualLLM(this, prompt, new com.ecodrive.api.DualLLMRouter.LLMCallback() {
            @Override
            public void onResponse(String response, boolean usedFallback) {
                runOnUiThread(() -> {
                    if (pbAiThinking != null) pbAiThinking.setVisibility(View.GONE);
                    if (tvAiThoughtStream != null) tvAiThoughtStream.setText("\"" + response.replaceAll("\\[CONFIDENCE: \\d+\\]", "").trim() + "\"");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (pbAiThinking != null) pbAiThinking.setVisibility(View.GONE);
                    com.ecodrive.utils.AppLog.e("AI_THOUGHT", "Failed", new Exception(error));
                });
            }
        });
    }

    private void fetchLiveTelemetry(boolean isSilent) {
        if (!isSilent) {
            btnFetchTelemetry.setEnabled(false);
            btnFetchTelemetry.setText("Fetching Live Data...");
        }

        String endLoc = etEndLoc.getText().toString().trim();
        // Allow empty destinations to fetch local weather using startLoc as endLoc automatically

        // Use exact GPS coordinates if available to prevent Nominatim geocoding failures
        String startLoc = tvCurrentLocation.getText().toString();
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            org.osmdroid.util.GeoPoint gp = myLocationOverlay.getMyLocation();
            startLoc = gp.getLatitude() + "," + gp.getLongitude();
        } else if (startLoc.equals("Fetching GPS...") || startLoc.contains("Unknown") || startLoc.contains("Unavailable")) {
            // Provide a sensible default fallback (e.g. New Delhi) so the UI doesn't just hang on "Awaiting Data" if emulator GPS is off
            startLoc = "28.6139,77.2090"; 
            if (!isSilent) {
                Toast.makeText(this, "GPS unavailable, using default location.", Toast.LENGTH_SHORT).show();
            }
        }

        // Log searched routes to HistoryManager for personalized AI context
        if (!startLoc.equals("28.6139,77.2090")) {
            com.ecodrive.data.HistoryManager.addSearchedRoute(this, "Location: " + startLoc);
        }
        if (endLoc != null && !endLoc.trim().isEmpty()) {
            com.ecodrive.data.HistoryManager.addSearchedRoute(this, "Destination: " + endLoc);
        }

        com.ecodrive.api.TelemetryFetcher.fetchTelemetryAsynchronously(
            startLoc, endLoc,
            new com.ecodrive.api.TelemetryCallback() {
                @Override
                public void onSuccess(double distanceKm, double durationSec, double tempC, 
                            double humidity, double windSpeedKmh, String condition,
                            double stopRatio, String terrainType, double routeAverageSpeedKmh, 
                            String geometryEncoded, String majorRoadsSummary) {
                        
                        com.ecodrive.utils.AppLog.i("Telemetry", "Live data fetched for route: " + majorRoadsSummary);
                        
                        if (!isSilent) {
                            btnFetchTelemetry.setEnabled(true);
                            btnFetchTelemetry.setText("Fetch Live OpenStreetMap Data");
                        }

                        // Update physical route properties
                        if (distanceKm > 0) {
                            etDistance.setText(String.format(java.util.Locale.US, "%.1f", distanceKm));
                        }

                        double speedKmh = routeAverageSpeedKmh > 0 ? routeAverageSpeedKmh
                            : (durationSec > 0 ? (distanceKm / durationSec) * 3600.0 : 0.0);
                        etSpeed.setText(String.format(java.util.Locale.US, "%.1f", speedKmh));

                        // Update dynamic stop ratio calculated from OSRM intersections
                        etStopRatio.setText(String.format(java.util.Locale.US, "%.2f", stopRatio));

                        // Update Weather UI
                        if (tvMainTemp != null) {
                            tvMainTemp.setText(String.format(java.util.Locale.US, "%.0f°", tempC));
                            if (tvWeatherCondition != null) tvWeatherCondition.setText(condition);
                            if (tvWindSpeed != null) tvWindSpeed.setText(String.format(java.util.Locale.US, "%.1f km/h", windSpeedKmh));
                            if (tvHumidity != null) tvHumidity.setText(String.format(java.util.Locale.US, "%.0f%%", humidity));
                            if (tvWeatherStatus != null) tvWeatherStatus.setText("Updated " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date()));
                            
                            // Traffic Heuristic: speed vs distance
                            String traffic = "Fluid";
                            if (stopRatio > 0.4) traffic = "Heavy";
                            else if (stopRatio > 0.2) traffic = "Moderate";
                            if (tvTrafficCondition != null) tvTrafficCondition.setText(traffic);
                            
                            if (tvRoadArea != null && terrainType.length() > 0) tvRoadArea.setText(terrainType.substring(0, 1).toUpperCase() + terrainType.substring(1));
                            
                            if (cardLiveWeatherRoute != null) {
                                cardLiveWeatherRoute.setVisibility(View.VISIBLE);
                            }
                        }

                        // Set live Temp -> affects Air Density
                        MainActivity.this.currentTemperatureC = tempC;
                        MainActivity.this.currentTerrainType = terrainType;
                        MainActivity.this.currentRouteAverageSpeedKmh = speedKmh;
                        MainActivity.this.currentMajorRoadsSummary = majorRoadsSummary;

                        double terrainGradePercent = com.ecodrive.utils.GeoUtils.estimateTerrainGradeFromLabel(terrainType);
                        if (Math.abs(parseOrDefault(etGrade, 0.0)) < 0.001) {
                            etGrade.setText(String.format(java.util.Locale.US, "%.1f", terrainGradePercent));
                        }

                        // Draw route overlay and pan map
                        try {
                            if (geometryEncoded != null && !geometryEncoded.isEmpty()) {
                                List<org.osmdroid.util.GeoPoint> routePoints = com.ecodrive.utils.GeoUtils.decodePolyline(geometryEncoded);
                                runOnUiThread(() -> {
                                    if (currentRouteOverlay != null) {
                                        mapView.getOverlayManager().remove(currentRouteOverlay);
                                    }
                                    org.osmdroid.views.overlay.Polyline poly = new org.osmdroid.views.overlay.Polyline();
                                    poly.setPoints(routePoints);
                                    poly.getOutlinePaint().setColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                                    poly.getOutlinePaint().setStrokeWidth(6.0f);
                                    mapView.getOverlayManager().add(poly);
                                    currentRouteOverlay = poly;
                                    mapView.invalidate();
                                });
                            }
                            if (!isSilent && distanceKm > 0) {
                                Toast.makeText(MainActivity.this, "Route Calculated. Distance: " + distanceKm + "km", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        com.ecodrive.utils.AppLog.e("Telemetry", "Fetch Error", e);
                        if (!isSilent) {
                            btnFetchTelemetry.setEnabled(true);
                            btnFetchTelemetry.setText("Fetch Live OpenStreetMap Data");
                            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });

    }

    private double parseOrDefault(EditText et, double defVal) {
        try {
            return Double.parseDouble(et.getText().toString());
        } catch (Exception e) {
            et.setText(String.valueOf(defVal));
            return defVal;
        }
    }

    private void applyDemoScenario(double dist, double speed, double stop, double accel, double grade, boolean ac) {
        etDistance.setText(String.valueOf(dist));
        etSpeed.setText(String.valueOf(speed));
        etStopRatio.setText(String.valueOf(stop));
        etAccel.setText(String.valueOf(accel));
        etGrade.setText(String.valueOf(grade));
        switchAc.setChecked(ac);
        // Demo scenarios are stored as synthetic records so the dashboard can filter them out.
        calculateAndDisplayModular(true, true);
    }

    private void calculateAndDisplayModular(boolean saveToDatabase) {
        // Wrapper for demo scenarios (synchronous)
        calculateAndDisplayModularInternal(saveToDatabase, false);
    }

    private void calculateAndDisplayModular(boolean saveToDatabase, boolean isSyntheticTrip) {
        calculateAndDisplayModularInternal(saveToDatabase, isSyntheticTrip);
    }

    private void calculateAndDisplayModularInternal(boolean saveToDatabase, boolean isSyntheticTrip) {
        try {
            double distance = parseOrDefault(etDistance, 15.0);
            double speed = parseOrDefault(etSpeed, 40.0);
            double stopRatio = parseOrDefault(etStopRatio, 0.2);
            double accel = parseOrDefault(etAccel, 0.5);
            double grade = parseOrDefault(etGrade, 0.0);
            boolean acOn = switchAc.isChecked();

            // ✅ INPUT RANGE VALIDATION
            if (distance <= 0 || distance > 5000) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Distance must be 0.1 to 5000 km", Toast.LENGTH_SHORT).show();
                    etDistance.setText("15.0");
                });
                return;
            }

            if (speed < 0 || speed > 200) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Speed must be 0 to 200 km/h", Toast.LENGTH_SHORT).show();
                    etSpeed.setText("40.0");
                });
                return;
            }

            if (accel < -2 || accel > 5) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Acceleration must be -2 to 5 m/s²", Toast.LENGTH_SHORT).show();
                    etAccel.setText("0.5");
                });
                return;
            }

            if (grade < -30 || grade > 30) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Grade must be -30% to 30%", Toast.LENGTH_SHORT).show();
                    etGrade.setText("0.0");
                });
                return;
            }

            if (stopRatio < 0 || stopRatio > 1) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Stop ratio must be 0 to 1", Toast.LENGTH_SHORT).show();
                    etStopRatio.setText("0.2");
                });
                return;
            }

            if (Math.abs(grade) < 0.001) {
                grade = GeoUtils.estimateTerrainGradeFromLabel(currentTerrainType);
            }

            VehicleSpecs selectedVehicle = getSelectedVehicle();
            VehicleSpecs.FuelType fuelOverride = getSelectedFuelType();

            // Allow manual override for testing EV calculations
            selectedVehicle.fuelType = fuelOverride;
            selectedVehicle.isEV = (fuelOverride == VehicleSpecs.FuelType.EV);

            // Calculate dynamic air density based on current temperature (Ideal Gas Law: P
            // / RT)
            double dynamicAirDensity = 101325.0 / (287.05 * (currentTemperatureC + 273.15));

            // 2. Continuous deterministic values generated by Physics Model (Formulas 3.1)
            List<PhysicsFeatureSet> physicsFeatures = DynamicVehicleEmissions.generatePhysicsFeatures(
                    selectedVehicle, distance, speed, accel, grade, dynamicAirDensity, 10000);

            // 3. Real telemetry derived from inputs or GPS (Formulas 3.2, 3.4)
            double derivedSpeedDiffMss = accel * 0.45; // Delta V proxy: maps acceleration to speed differential
            TripTelemetry telemetry = new TripTelemetry(
                    derivedSpeedDiffMss, stopRatio, acOn, System.currentTimeMillis(), currentTerrainType,
                    currentRouteAverageSpeedKmh > 0 ? currentRouteAverageSpeedKmh : speed);

            // 4. ML Model blends Physics Output with real-world non-deterministic factors
            TripResult finalResult = mlModel.predictTripEmissions(physicsFeatures, telemetry, isSyntheticTrip);

                final double finalDistance = distance;
                final double finalSpeed = speed;
                final double finalStopRatio = stopRatio;
                final double finalAccel = accel;
                final double finalGrade = grade;
                final boolean finalAcOn = acOn;
                final double finalAirDensity = dynamicAirDensity;
                final VehicleSpecs finalVehicle = selectedVehicle;
                final VehicleSpecs.FuelType finalFuelOverride = fuelOverride;

            // 5. Encapsulated Report Generation (must update UI on main thread)
            runOnUiThread(() -> {
                String reportText = ReportGenerator.generateStringReport(finalResult)
                    + "\n\n"
                    + buildDetailedMetricsReport(finalResult, physicsFeatures.get(0), finalVehicle,
                        finalFuelOverride, finalDistance, finalSpeed, finalStopRatio, finalAccel, finalGrade, finalAcOn,
                        finalAirDensity, currentMajorRoadsSummary)
                    + "\n\n"
                    + TripAIAssistant.buildInsights(finalResult, finalVehicle, finalFuelOverride, finalDistance,
                        finalSpeed, finalStopRatio, currentTemperatureC, finalAcOn, currentTerrainType,
                        currentMajorRoadsSummary, finalAirDensity);

                tvResults.setText(reportText);

                // Populate Explainability Panel
                if (tvCalcDetails != null) {
                    tvCalcDetails.setText(buildDetailedMetricsReport(finalResult, physicsFeatures.get(0), finalVehicle,
                        finalFuelOverride, finalDistance, finalSpeed, finalStopRatio, finalAccel, finalGrade, finalAcOn,
                        finalAirDensity, currentMajorRoadsSummary));
                    
                    tvAiDetails.setText(TripAIAssistant.buildInsights(finalResult, finalVehicle, finalFuelOverride, finalDistance,
                        finalSpeed, finalStopRatio, currentTemperatureC, finalAcOn, currentTerrainType,
                        currentMajorRoadsSummary, finalAirDensity));
                    
                    // Show headers if they were hidden
                    tvCalcHeader.setVisibility(View.VISIBLE);
                    tvAiHeader.setVisibility(View.VISIBLE);
                }

                // Populate Visual Graphs
                populateCharts(finalResult, physicsFeatures.get(0));
            });

            if (saveToDatabase) {
                // Persist the calculation result.
                long timestamp = System.currentTimeMillis();
                com.ecodrive.data.TripEntity trip = new com.ecodrive.data.TripEntity(
                        timestamp, distance, finalResult.meanCo2Kg, physicsFeatures.get(0).baseCo2Kg,
                        (distance / 30.0) * stopRatio * 3600.0, "User Origin",
                        "User Destination", isSyntheticTrip);
                new Thread(() -> {
                    com.ecodrive.data.EcoDatabase.getDatabase(getApplicationContext()).tripDao().insertTrip(trip);
                }).start();
            }

        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Error in calculation: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            e.printStackTrace();
        }
    }

    private void populateCharts(TripResult result, PhysicsFeatureSet physics) {
        barChartEmissions.setVisibility(View.VISIBLE);
        pieChartEnergy.setVisibility(View.VISIBLE);

        // 1. Populate Bar Chart (Optimal vs Actual)
        java.util.ArrayList<BarEntry> barEntries = new java.util.ArrayList<>();
        float optimalBase = (float) (result.meanCo2Kg * 0.7); // Roughly estimated optimal baseline without traffic
                                                              // penalties
        barEntries.add(new BarEntry(1f, optimalBase)); // Optimal Profile
        barEntries.add(new BarEntry(2f, (float) result.meanCo2Kg)); // Actual Profile

        BarDataSet barDataSet = new BarDataSet(barEntries, "Optimal vs Actual Emission (kg)");
        barDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        barDataSet.setValueTextColor(android.graphics.Color.WHITE);
        barDataSet.setValueTextSize(10f);

        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.5f);
        barChartEmissions.setData(barData);
        barChartEmissions.getXAxis().setDrawLabels(false);
        barChartEmissions.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        barChartEmissions.getAxisRight().setEnabled(false);
        barChartEmissions.getLegend().setTextColor(android.graphics.Color.WHITE);
        barChartEmissions.animateY(1000);
        barChartEmissions.invalidate();

        // 2. Populate Pie Chart (Emissions Breakdown)
        java.util.ArrayList<PieEntry> pieEntries = new java.util.ArrayList<>();

        float baseCo2 = (float) physics.baseCo2Kg;
        float contextualPenalty = Math.max(0f, (float) (result.meanCo2Kg - baseCo2));

        if (baseCo2 > 0)
            pieEntries.add(new PieEntry(baseCo2, "Base Vehicle Physics"));
        if (contextualPenalty > 0)
            pieEntries.add(new PieEntry(contextualPenalty, "Traffic/AC Load Penalty"));

        PieDataSet pieDataSet = new PieDataSet(pieEntries, "Emissions Source");
        pieDataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        pieDataSet.setValueTextColor(android.graphics.Color.WHITE);
        pieDataSet.setValueTextSize(12f);

        PieData pieData = new PieData(pieDataSet);
        pieChartEnergy.setData(pieData);
        pieChartEnergy.setHoleColor(android.graphics.Color.TRANSPARENT);
        pieChartEnergy.setCenterText("CO2 Source\nBreakdown");
        pieChartEnergy.setCenterTextColor(android.graphics.Color.WHITE);
        pieChartEnergy.getLegend().setTextColor(android.graphics.Color.WHITE);
        pieChartEnergy.animateY(1000);
        pieChartEnergy.invalidate();
    }

    private String buildDetailedMetricsReport(
            TripResult result,
            PhysicsFeatureSet physics,
            VehicleSpecs vehicle,
            VehicleSpecs.FuelType fuelType,
            double distanceKm,
            double avgSpeedKmh,
            double stopRatio,
            double accelMss,
            double gradePercent,
            boolean acOn,
            double airDensityKgM3,
            String majorRoadsSummary) {

        double gPerKm = distanceKm > 0 ? (result.meanCo2Kg * 1000.0 / distanceKm) : 0.0;
        double fuelPer100Km = distanceKm > 0 ? (result.meanFuelLitres * 100.0 / distanceKm) : 0.0;
        double contextualPenaltyKg = Math.max(0.0, result.meanCo2Kg - physics.baseCo2Kg);
        double acFactor = acOn ? 0.85 : 1.0;
        double trafficFactor = 1.0 + (stopRatio * 0.35);

        String vehicleLabel = (vehicle != null && vehicle.modelName != null) ? vehicle.modelName : "Unknown Vehicle";

        return String.format(Locale.US,
                "Detailed Metrics\n"
                        + "Distance travelled: %.2f km\n"
                        + "Fuel burned: %.2f L\n"
                        + "CO2 intensity: %.1f g/km\n"
                        + "Fuel efficiency: %.2f L/100km\n"
                        + "Route taken (major): %s\n"
                        + "Vehicle profile: %s | Fuel: %s\n"
                        + "\n"
                        + "Calculation Transparency\n"
                        + "Formula used: FinalCO2 ~= BasePhysicsCO2 x TrafficFactor x ACFactor + ContextPenalty\n"
                        + "Base physics CO2: %.3f kg\n"
                        + "Context/telemetry penalty: %.3f kg\n"
                        + "Stop ratio (traffic): %.3f -> TrafficFactor %.3f\n"
                        + "AC state: %s -> ACFactor %.3f\n"
                        + "Weather temperature: %.1f C\n"
                        + "Air density used: %.4f kg/m3\n"
                        + "Terrain: %s | Grade: %.2f%%\n"
                        + "Avg speed: %.1f km/h | Acceleration input: %.2f m/s2\n"
                        + "\n"
                        + "Validated CO2 Metrics\n"
                        + "Mean CO2: %.3f kg\n"
                        + "95%% confidence interval: [%.3f, %.3f] kg\n"
                        + "Energy required: %.3f MJ\n",
                distanceKm,
                result.meanFuelLitres,
                gPerKm,
                fuelPer100Km,
                majorRoadsSummary,
                vehicleLabel,
                fuelType != null ? fuelType.name() : "UNKNOWN",
                physics.baseCo2Kg,
                contextualPenaltyKg,
                stopRatio,
                trafficFactor,
                acOn ? "ON" : "OFF",
                acFactor,
                currentTemperatureC,
                airDensityKgM3,
                currentTerrainType,
                gradePercent,
                avgSpeedKmh,
                accelMss,
                result.meanCo2Kg,
                result.lowerBoundCo2Kg,
                result.upperBoundCo2Kg,
                result.energyReqMj);
    }

    private void toggleVisibility(android.view.View v) {
        if (v.getVisibility() == android.view.View.VISIBLE) {
            v.setVisibility(android.view.View.GONE);
        } else {
            v.setVisibility(android.view.View.VISIBLE);
        }
    }
}
