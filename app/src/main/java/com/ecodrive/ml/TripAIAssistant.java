package com.ecodrive.ml;

import com.ecodrive.physics.TripResult;
import com.ecodrive.physics.VehicleSpecs;

import java.util.Locale;

/**
 * Lightweight, on-device AI explainer that converts metrics into concrete,
 * factual actions. This is rule-based AI, not a remote LLM call.
 */
public final class TripAIAssistant {

    private TripAIAssistant() {
    }

    public static String buildInsights(
            TripResult result,
            VehicleSpecs vehicle,
            VehicleSpecs.FuelType fuelType,
            double distanceKm,
            double avgSpeedKmh,
            double stopRatio,
            double temperatureC,
            boolean acOn,
            String terrainType,
            String majorRoadsSummary,
            double airDensityKgM3) {

        StringBuilder sb = new StringBuilder();
        sb.append("AI Insights (On-device, Evidence-based)\n");
        sb.append("Model in use: Physics + ML fusion (Monte Carlo + telemetry).\n\n");

        double gPerKm = distanceKm > 0 ? (result.meanCo2Kg * 1000.0 / distanceKm) : 0.0;
        sb.append(String.format(Locale.US,
                "Trip intensity: %.1f gCO2/km, fuel %.2f L, uncertainty range [%.2f, %.2f] kg.\n",
                gPerKm, result.meanFuelLitres, result.lowerBoundCo2Kg, result.upperBoundCo2Kg));

        if (avgSpeedKmh < 20.0) {
            sb.append("- Traffic pattern detected: low average speed. Avoiding peak windows can cut idling emissions.\n");
        } else if (avgSpeedKmh > 90.0) {
            sb.append("- High-speed aerodynamic penalty detected. Cruising near 60-80 km/h improves efficiency.\n");
        } else {
            sb.append("- Speed band is near efficient range for most vehicles.\n");
        }

        if (stopRatio > 0.45) {
            sb.append("- Frequent stop-go pattern detected. Prefer signal-synchronized corridors or limited-stop roads.\n");
        }

        if (acOn && temperatureC > 30.0) {
            sb.append("- AC penalty active in hot weather; cabin pre-cool and recirculation mode can reduce load.\n");
        }

        if ("hilly".equalsIgnoreCase(terrainType) || "rolling".equalsIgnoreCase(terrainType)) {
            sb.append("- Terrain elevation load detected. Smoother throttle and lower acceleration spikes help in gradients.\n");
        }

        sb.append(String.format(Locale.US,
                "- Air density used in physics: %.3f kg/m3 (from weather %.1f C).\n",
                airDensityKgM3, temperatureC));

        sb.append("- Route major roads: ").append(majorRoadsSummary).append("\n");

        if (fuelType == VehicleSpecs.FuelType.PETROL || fuelType == VehicleSpecs.FuelType.DIESEL) {
            sb.append("- ICE recommendation: maintain tire pressure, reduce hard accelerations, and idle less than 30s where safe.\n");
        } else if (fuelType == VehicleSpecs.FuelType.CNG) {
            sb.append("- CNG recommendation: keep injectors/tune health checked; low-speed stop-go still increases total CO2.\n");
        } else if (fuelType == VehicleSpecs.FuelType.EV) {
            sb.append("- EV recommendation: maximize regenerative braking and prefer steady speed over rapid acceleration.\n");
        }

        sb.append("\nAnalysis powered by on-device physics engine with Monte Carlo uncertainty quantification.");
        return sb.toString();
    }
}
