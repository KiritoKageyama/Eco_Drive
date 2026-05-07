package com.ecodrive.reports;

import com.ecodrive.physics.TripResult;

import java.util.Locale;

public class ReportGenerator {

    /**
     * Formats the final trip result into a clean, displayable String.
     */
    public static String generateStringReport(TripResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Modular Trip Evaluation ---\n\n");
        sb.append(result.formatSummary()).append("\n\n");

        sb.append(String.format(Locale.US, "95%% CI Statistical Bounds:\n[%.3f kg, %.3f kg] CO2\n\n",
                result.lowerBoundCo2Kg, result.upperBoundCo2Kg));

        sb.append("Intelligent ML Insights:\n").append(result.recommendations);

        return sb.toString();
    }
}
