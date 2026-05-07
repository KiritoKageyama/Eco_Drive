package com.ecodrive.ml;

import android.content.Context;
import com.ecodrive.data.VehicleDatabase;
import com.ecodrive.physics.VehicleSpecs;
import com.ecodrive.api.DualLLMRouter;

import java.util.Locale;

public class AutonomousAgent {

    public interface InsightCallback {
        void onInsightReady(String insight);
    }

    public static void generateWeeklyInsight(Context context, double weeklyDistance, double weeklyCo2, int hardStops, String commonTerrain, InsightCallback callback) {
        VehicleSpecs currentVehicle = null;
        try {
            VehicleDatabase.init(context);
            if (!VehicleDatabase.getAllVehicles().isEmpty()) {
                currentVehicle = VehicleDatabase.getAllVehicles().get(0);
            }
        } catch (Exception ignored) {}

        String vName = currentVehicle != null ? currentVehicle.modelName : "Unknown Vehicle";
        
        String prompt = String.format(Locale.US,
                "You are the EcoDrive Autonomous AI Agent. Generate a highly detailed, professional, and personalized weekly telemetry report for the user.\n" +
                "- Vehicle: %s\n" +
                "- Weekly Distance: %.1f km\n" +
                "- Weekly CO2 Emitted: %.2f kg\n" +
                "- Hard Stops / Aggressive Braking: %d\n" +
                "- User History & Terrain Context: %s\n\n" +
                "Do NOT write a superficial 3-sentence summary. Instead, write a comprehensive, multi-paragraph report. " +
                "Include a breakdown of their emissions, how their searched locations/history influence their driving patterns, and specific, actionable advice to reduce CO2 footprint. " +
                "CRITICAL: You must end your response with a strict confidence score on a scale of 0 to 100 in this exact format: [CONFIDENCE: 85]. " +
                "If you lack data to make an accurate analysis, give a low score.",
                vName, weeklyDistance, weeklyCo2, hardStops, commonTerrain);

        DualLLMRouter.queryDualLLM(context, prompt, new DualLLMRouter.LLMCallback() {
            @Override
            public void onResponse(String response, boolean usedFallback) {
                // Strip the [CONFIDENCE: X] tag before showing to user
                String cleanResponse = response.replaceAll("\\[CONFIDENCE: \\d+\\]", "").trim();
                if (usedFallback) {
                    cleanResponse += "\n\n(Insights powered by Gemini API - Primary AI Confidence Low)";
                }
                callback.onInsightReady(cleanResponse);
            }

            @Override
            public void onError(String error) {
                callback.onInsightReady("Agent Error: " + error);
            }
        });
    }
}
