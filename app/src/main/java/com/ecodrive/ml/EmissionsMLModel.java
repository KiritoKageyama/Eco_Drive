package com.ecodrive.ml;

import com.ecodrive.physics.PhysicsFeatureSet;
import com.ecodrive.physics.TripResult;

import java.util.Arrays;
import java.util.List;

/**
 * The Machine Learning Node: Takes deterministic physics continuous outputs
 * and blends them with real-world telemetry (Traffic, AC, Timestamps) to
 * simulate the
 * final predictions outlined in formulas 3.2 and 3.4.
 */
public class EmissionsMLModel {

    // Lambda constants for PhiDriving enrichment
    private static final double LAMBDA_ACCEL = 0.10;
    private static final double LAMBDA_JERK = 0.05; // Base proxy

    /**
     * Fuses Physics output with Telemetry to generate final Results.
     * @param isSynthetic true if this is demo/test data, false if real trip
     */
    public TripResult predictTripEmissions(List<PhysicsFeatureSet> physicsFeatures, TripTelemetry telemetry, boolean isSynthetic) {

        int iterations = physicsFeatures.size();
        double[] finalCo2Results = new double[iterations];
        double sumFuelLitres = 0.0;
        double sumEnergyJ = 0.0;

        double baseDistance = physicsFeatures.isEmpty() ? 0.0 : physicsFeatures.get(0).distanceKm;
        boolean isEV = physicsFeatures.isEmpty() ? false : physicsFeatures.get(0).isEV;

        // 1. Calculate explicit idle/traffic emissions based on congestion (No
        // arbitrary scalar)
        double referenceSpeedKmh = telemetry.routeAverageSpeedKmh > 0.1 ? telemetry.routeAverageSpeedKmh : 30.0;
        double estimatedTripTimeHours = baseDistance / referenceSpeedKmh;
        double idleTimeHours = estimatedTripTimeHours * telemetry.trafficCongestionRatio;

        // Explicit Idle Fuel/Energy
        double idleFuelLitres = idleTimeHours * 0.8; // Approx 0.8 L/hr for average ICE
        double idleCo2KgIce = idleFuelLitres * 2.31; // Using petrol EF

        double idleEvKwh = idleTimeHours * 0.5; // Approx 0.5 kW for EV baseline electronics/AC
        double idleCo2KgEv = idleEvKwh * 0.716; // Using Indian grid average

        // AC Penalty (F_AC)
        double acFactor = telemetry.isAcOn ? 0.85 : 1.0; // 15% efficiency loss

        double terrainMultiplier = terrainMultiplier(telemetry.terrainType);

        // Dynamic Enrichment factor (Phi_driving) - scales with aggression
        double dynamicLambdaAccel = 0.05 + Math.min(telemetry.speedDiffMss / 10.0, 0.10);
        double phiDriving = 1.0 + (dynamicLambdaAccel * telemetry.speedDiffMss) + LAMBDA_JERK;

        // 2. Apply ML/Formula Multipliers to each Physics Monte Carlo sample
        for (int i = 0; i < iterations; i++) {
            PhysicsFeatureSet p = physicsFeatures.get(i);

            // Adjust fuel/energy demands with AC
            double adjustedEnergyJ = p.requiredMechanicalEnergyJ / acFactor;
            sumEnergyJ += adjustedEnergyJ;

            double finalCo2 = 0.0;

            if (p.isEV) {
                // EV Final: Base CO2 * Driver Aggression / AC + Explicit Idle
                finalCo2 = ((p.baseCo2Kg * phiDriving * terrainMultiplier) / acFactor) + idleCo2KgEv;
            } else {
                // ICE Final
                double adjustedFuel = ((p.rawFuelLitres * phiDriving) / acFactor) + idleFuelLitres;
                sumFuelLitres += adjustedFuel;

                finalCo2 = ((p.baseCo2Kg * phiDriving * terrainMultiplier) / acFactor) + idleCo2KgIce;
            }

            finalCo2Results[i] = finalCo2;
        }

        // 3. Aggregate Statistical Bounds
        Arrays.sort(finalCo2Results);
        double meanCo2 = Arrays.stream(finalCo2Results).average().orElse(0.0);

        int lowerIndex = (int) (iterations * 0.05);
        int upperIndex = (int) (iterations * 0.95);
        double lowerBound = finalCo2Results[Math.max(0, lowerIndex)];
        double upperBound = finalCo2Results[Math.min(iterations - 1, upperIndex)];

        double meanFuelLitres = sumFuelLitres / iterations;
        double meanEnergyMj = (sumEnergyJ / iterations) / 1000000.0;

        String recommendations = generateRecommendations(telemetry);

        return new TripResult(meanCo2, lowerBound, upperBound, meanFuelLitres, meanEnergyMj, isEV, recommendations, isSynthetic);
    }

    private String generateRecommendations(TripTelemetry telemetry) {
        StringBuilder sb = new StringBuilder();
        if (telemetry.speedDiffMss > 1.0)
            sb.append("- Reduce harsh acceleration.\n");
        if (telemetry.trafficCongestionRatio > 0.3)
            sb.append("- Seek alternate routes with less stop-and-go.\n");
        if (telemetry.isAcOn)
            sb.append("- Turn off AC in mild weather.\n");
        if (telemetry.terrainType != null && telemetry.terrainType.equalsIgnoreCase("hilly"))
            sb.append("- On hilly roads, keep steady throttle and avoid repeated acceleration.\n");
        if (sb.length() == 0)
            sb.append("- Optimal driving behavior detected!");
        return sb.toString().trim();
    }

    private double terrainMultiplier(String terrainType) {
        if (terrainType == null) {
            return 1.0;
        }

        switch (terrainType.toLowerCase()) {
            case "hilly":
                return 1.12;
            case "rolling":
                return 1.06;
            case "urban":
                return 1.03;
            case "arterial":
                return 1.01;
            case "plains":
            default:
                return 1.0;
        }
    }
}
