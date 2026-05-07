package com.ecodrive.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.location.Location;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import com.ecodrive.R;
import com.ecodrive.api.TelemetryFetcher;
import com.ecodrive.api.TelemetryCallback;
import com.ecodrive.data.VehicleDatabase;
import com.ecodrive.ml.EmissionsMLModel;
import com.ecodrive.ml.TripAIAssistant;
import com.ecodrive.physics.DynamicVehicleEmissions;
import com.ecodrive.physics.PhysicsFeatureSet;
import com.ecodrive.physics.TripResult;
import com.ecodrive.physics.VehicleSpecs;
import com.ecodrive.utils.GeoUtils;

import java.util.List;

public class DynamicTestActivity extends AppCompatActivity {

    private EditText etTestDestination;
    private EditText etTestAvgSpeed;
    private Button btnCalculateRoute;
    private TextView tvTestResults;
    private MapView mapViewTest;
    private Polyline currentRouteOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private android.widget.AutoCompleteTextView spinnerTestCategory, spinnerTestFuel;
    private android.widget.Spinner spinnerScenarios;

    private TextView tvScenarioDescription;


    private EmissionsMLModel mlModel = new EmissionsMLModel();
    private double routeDistanceKm = 0.0;
    private double routeDurationSec = 0.0;
    private String currentTerrainType = "unknown";
    private double currentTemperatureC = 25.0;
    private String currentMajorRoadsSummary = "No major roads detected";
    private double avgSpeed = 40.0; // Member variable to store user input

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize OSM configuration
        Configuration.getInstance().load(getApplicationContext(),
                getApplicationContext().getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("EcoDrive/2.1 (support@ecodrive.app)");

        setContentView(R.layout.activity_dynamic_test);

        // Initialize views
        etTestDestination = findViewById(R.id.etTestDestination);
        etTestAvgSpeed = findViewById(R.id.etTestAvgSpeed);
        btnCalculateRoute = findViewById(R.id.btnCalculateRoute);
        tvTestResults = findViewById(R.id.tvTestResults);
        mapViewTest = findViewById(R.id.mapViewTest);
        spinnerTestCategory = findViewById(R.id.spinnerTestCategory);
        spinnerTestFuel = findViewById(R.id.spinnerTestFuel);

        setupVehicleSpinners();


        // Setup map
        mapViewTest.setMultiTouchControls(true);
        mapViewTest.getController().setZoom(12.0);
        mapViewTest.getController().setCenter(new GeoPoint(28.6139, 77.2090));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize Scenarios
        spinnerScenarios = findViewById(R.id.spinnerScenarios);
        tvScenarioDescription = findViewById(R.id.tvScenarioDescription);
        
        List<ScenarioManager.Scenario> scenarios = ScenarioManager.getScenarios();
        android.widget.ArrayAdapter<ScenarioManager.Scenario> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, scenarios);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScenarios.setAdapter(adapter);

        spinnerScenarios.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ScenarioManager.Scenario s = scenarios.get(position);
                etTestDestination.setText(s.destination);
                etTestAvgSpeed.setText(String.valueOf(s.avgSpeed));
                tvScenarioDescription.setText(s.description);
                Toast.makeText(DynamicTestActivity.this, "Scenario loaded: " + s.name, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Setup toolbar


        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarDynamicTest);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Calculate button listener
        btnCalculateRoute.setOnClickListener(v -> calculateDynamicRoute());
    }

    private void calculateDynamicRoute() {
        String destination = etTestDestination.getText().toString().trim();
        String speedStr = etTestAvgSpeed.getText().toString().trim();

        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speedStr.isEmpty()) {
            Toast.makeText(this, "Please enter average speed (km/h)", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            this.avgSpeed = Double.parseDouble(speedStr);
            if (this.avgSpeed <= 0 || this.avgSpeed > 300) {
                Toast.makeText(this, "Speed must be between 1 and 300 km/h", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid speed value", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current location from GPS, fallback to Dehradun coordinates if unavailable
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                String currentLocation = "30.3165,78.0322"; // Default fallback
                if (location != null) {
                    currentLocation = location.getLatitude() + "," + location.getLongitude();
                }
                executeTelemetryFetch(currentLocation, destination);
            }).addOnFailureListener(this, e -> {
                executeTelemetryFetch("30.3165,78.0322", destination);
            });
        } else {
            executeTelemetryFetch("30.3165,78.0322", destination);
        }
    }

    private void executeTelemetryFetch(String currentLocation, String destination) {
        btnCalculateRoute.setEnabled(false);
        btnCalculateRoute.setText("Calculating...");

        // Fetch telemetry from current location to destination
        TelemetryFetcher.fetchTelemetryAsynchronously(
                currentLocation, destination,
                new TelemetryCallback() {
                    @Override
                    public void onSuccess(double distanceKm, double durationSec, double tempC, 
                            double humidity, double windSpeedKmh, String condition,
                            double stopRatio, String terrainType, double routeAverageSpeedKmh, 
                            String geometryEncoded, String majorRoadsSummary) {
                        DynamicTestActivity.this.routeDistanceKm = distanceKm;
                        DynamicTestActivity.this.routeDurationSec = durationSec;
                        DynamicTestActivity.this.currentTemperatureC = tempC;
                        DynamicTestActivity.this.currentTerrainType = terrainType;
                        DynamicTestActivity.this.currentMajorRoadsSummary = majorRoadsSummary;


                        runOnUiThread(() -> {
                            // Draw route on map
                            if (geometryEncoded != null && !geometryEncoded.isEmpty()) {
                                List<GeoPoint> routePoints = GeoUtils.decodePolyline(geometryEncoded);
                                if (currentRouteOverlay != null) {
                                    mapViewTest.getOverlayManager().remove(currentRouteOverlay);
                                }
                                Polyline poly = new Polyline();
                                poly.setPoints(routePoints);
                                poly.getOutlinePaint()
                                        .setColor(ContextCompat.getColor(DynamicTestActivity.this, R.color.colorAccent));
                                poly.getOutlinePaint().setStrokeWidth(6.0f);
                                mapViewTest.getOverlayManager().add(poly);
                                currentRouteOverlay = poly;
                                mapViewTest.invalidate();

                                // Center map on first point
                                if (!routePoints.isEmpty()) {
                                    mapViewTest.getController().animateTo(routePoints.get(0));
                                }
                            }

                            // Calculate CO2 emissions with user-provided speed
                            calculateAndDisplayCO2(distanceKm, DynamicTestActivity.this.avgSpeed);

                            btnCalculateRoute.setEnabled(true);
                            btnCalculateRoute.setText("Calculate Route & CO2");
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            Toast.makeText(DynamicTestActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                            btnCalculateRoute.setEnabled(true);
                            btnCalculateRoute.setText("Calculate Route & CO2");
                        });
                    }
                });
    }

    private void calculateAndDisplayCO2(double distanceKm, double avgSpeedKmh) {
        VehicleDatabase.init(this);

        VehicleSpecs selectedVehicle = null;
        String selectedModel = spinnerTestCategory.getText().toString();
        List<VehicleSpecs> all = VehicleDatabase.getAllVehicles();
        for (VehicleSpecs v : all) {
            if (v.modelName.equals(selectedModel)) {
                selectedVehicle = v;
                break;
            }
        }
        
        if (selectedVehicle == null) {
            selectedVehicle = all.get(0);
        }

        String fuelStr = spinnerTestFuel.getText().toString().toUpperCase(java.util.Locale.US);
        VehicleSpecs.FuelType fuelEnum = VehicleSpecs.FuelType.PETROL;
        try {
            if (fuelStr.equals("ELECTRIC")) fuelEnum = VehicleSpecs.FuelType.EV;
            else fuelEnum = VehicleSpecs.FuelType.valueOf(fuelStr);
        } catch (Exception ignored) {}

        // Override fuel type if user selected something else
        if (selectedVehicle != null) {
            selectedVehicle = new VehicleSpecs(selectedVehicle.modelName, selectedVehicle.massKg, 
                selectedVehicle.engineCc, selectedVehicle.frontalAreaM2, selectedVehicle.dragCoefficient,
                selectedVehicle.category, fuelEnum);
        }

        try {


            // Estimate terrain grade from terrain type
            double gradePercent = 0.0;
            if ("mountainous".equalsIgnoreCase(currentTerrainType)) {
                gradePercent = 8.0;
            } else if ("hilly".equalsIgnoreCase(currentTerrainType)) {
                gradePercent = 4.0;
            }

            double accelMagnitude = 0.5;
            double dynamicAirDensity = 101325.0 / (287.05 * (currentTemperatureC + 273.15));

            // Generate physics features using the proper static method
            List<com.ecodrive.physics.PhysicsFeatureSet> physicsFeatures = 
                    com.ecodrive.physics.DynamicVehicleEmissions.generatePhysicsFeatures(
                    selectedVehicle, distanceKm, avgSpeedKmh, accelMagnitude, gradePercent, 
                    dynamicAirDensity, 10000);

            // Create telemetry with route info
            com.ecodrive.ml.TripTelemetry telemetry = new com.ecodrive.ml.TripTelemetry(
                    0.45, // Derived speed differential proxy
                    0.0,  // stop ratio
                    false, // AC off
                    System.currentTimeMillis(),
                    currentTerrainType,
                    avgSpeedKmh);

            // Use ML model to predict CO2
            com.ecodrive.physics.TripResult result = mlModel.predictTripEmissions(physicsFeatures, telemetry, false);

            // Display results
            String resultsText = String.format(
                    "🗺️ Route Analysis\n" +
                            "Distance: %.2f km\n" +
                            "Duration: %.1f min\n" +
                            "Avg Speed: %.1f km/h\n" +
                            "Terrain: %s\n" +
                        "Major roads: %s\n" +
                            "Temperature: %.1f°C\n\n" +
                            "📊 CO2 Emissions\n" +
                            "Predicted: %.2f kg CO2\n" +
                            "Range: %.2f - %.2f kg\n" +
                            "Fuel: %.2f L\n" +
                            "Efficiency: %.1f g/km",
                    distanceKm,
                    routeDurationSec / 60.0,
                    avgSpeedKmh,
                    currentTerrainType,
                    currentMajorRoadsSummary,
                    currentTemperatureC,
                    result.meanCo2Kg,
                    result.lowerBoundCo2Kg,
                    result.upperBoundCo2Kg,
                    result.meanFuelLitres,
                    (result.meanCo2Kg * 1000) / distanceKm);

                String aiText = "\n\n" + TripAIAssistant.buildInsights(
                    result,
                    selectedVehicle,
                    selectedVehicle.fuelType,
                    distanceKm,
                    avgSpeedKmh,
                    0.0,
                    currentTemperatureC,
                    false,
                    currentTerrainType,
                    currentMajorRoadsSummary,
                    dynamicAirDensity);

                tvTestResults.setText(resultsText + aiText);
            tvTestResults.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            tvTestResults.setText("Error calculating emissions: " + e.getMessage());
            tvTestResults.setVisibility(View.VISIBLE);
        }
    }

    private void setupVehicleSpinners() {
        VehicleDatabase.init(this);
        List<VehicleSpecs> vehicles = VehicleDatabase.getAllVehicles();
        List<String> names = new java.util.ArrayList<>();
        for (VehicleSpecs v : vehicles) names.add(v.modelName);

        android.widget.ArrayAdapter<String> catAdapter = new android.widget.ArrayAdapter<>(this, R.layout.item_dropdown, names);
        spinnerTestCategory.setAdapter(catAdapter);
        if (!names.isEmpty()) spinnerTestCategory.setText(names.get(0), false);

        List<String> fuels = new java.util.ArrayList<>();
        fuels.add("Petrol"); fuels.add("Diesel"); fuels.add("Electric"); fuels.add("CNG");
        android.widget.ArrayAdapter<String> fuelAdapter = new android.widget.ArrayAdapter<>(this, R.layout.item_dropdown, fuels);
        spinnerTestFuel.setAdapter(fuelAdapter);
        spinnerTestFuel.setText("Petrol", false);
    }

    @Override
    public void onResume() {

        super.onResume();
        if (mapViewTest != null)
            mapViewTest.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapViewTest != null)
            mapViewTest.onPause();
    }
}
