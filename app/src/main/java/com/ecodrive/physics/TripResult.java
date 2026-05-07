package com.ecodrive.physics;

import java.io.Serializable;
import java.util.Locale;

public class TripResult implements Serializable {
    private static final long serialVersionUID = 1L;
    public final double meanCo2Kg;
    public final double lowerBoundCo2Kg;
    public final double upperBoundCo2Kg;
    public final double meanFuelLitres;
    public final double energyReqMj; // For EV or detailed analysis
    public final boolean isEV;
    public final String recommendations;
    public final boolean isSynthetic; // True if demo/test data, False if real trip

    public TripResult(double meanCo2Kg, double lowerBoundCo2Kg, double upperBoundCo2Kg,
            double meanFuelLitres, double energyReqMj, boolean isEV, String recommendations,
            boolean isSynthetic) {
        this.meanCo2Kg = meanCo2Kg;
        this.lowerBoundCo2Kg = lowerBoundCo2Kg;
        this.upperBoundCo2Kg = upperBoundCo2Kg;
        this.meanFuelLitres = meanFuelLitres;
        this.energyReqMj = energyReqMj;
        this.isEV = isEV;
        this.recommendations = recommendations;
        this.isSynthetic = isSynthetic;
    }

    public String formatSummary() {
        if (isEV) {
            return String.format(Locale.US, "Est. CO2: %.2f kg (±%.1f%%)\nEnergy: %.2f kWh",
                    meanCo2Kg, getUncertaintyPercentage(), energyReqMj / 3.6);
        } else {
            return String.format(Locale.US, "Est. CO2: %.2f kg (±%.1f%%)\nFuel: %.2f L",
                    meanCo2Kg, getUncertaintyPercentage(), meanFuelLitres);
        }
    }

    private double getUncertaintyPercentage() {
        if (meanCo2Kg == 0)
            return 0.0;
        double diff = Math.max(Math.abs(upperBoundCo2Kg - meanCo2Kg), Math.abs(meanCo2Kg - lowerBoundCo2Kg));
        return (diff / meanCo2Kg) * 100.0;
    }
}
