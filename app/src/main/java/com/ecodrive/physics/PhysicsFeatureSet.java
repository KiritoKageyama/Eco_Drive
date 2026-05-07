package com.ecodrive.physics;

/**
 * Represents the continuous raw output from the isolated Physics generation
 * layer.
 * This object is passed directly into the ML Model module.
 */
public class PhysicsFeatureSet {
    public final double requiredMechanicalEnergyJ;
    public final double rawFuelLitres; // Deterministic without traffic/slope multipliers
    public final double rawEvKwh; // Deterministic EV without multipliers
    public final double baseCo2Kg; // Deterministic CO2 without multipliers
    public final double distanceKm;
    public final boolean isEV;
    public final String modelName;

    // Constants extracted during physics generation
    public final double sampledCd;
    public final double sampledCrr;
    public final double sampledEtaEng;

    public PhysicsFeatureSet(double requiredMechanicalEnergyJ, double rawFuelLitres, double rawEvKwh, double baseCo2Kg,
            double distanceKm, boolean isEV, String modelName,
            double sampledCd, double sampledCrr, double sampledEtaEng) {
        this.requiredMechanicalEnergyJ = requiredMechanicalEnergyJ;
        this.rawFuelLitres = rawFuelLitres;
        this.rawEvKwh = rawEvKwh;
        this.baseCo2Kg = baseCo2Kg;
        this.distanceKm = distanceKm;
        this.isEV = isEV;
        this.modelName = modelName;
        this.sampledCd = sampledCd;
        this.sampledCrr = sampledCrr;
        this.sampledEtaEng = sampledEtaEng;
    }
}
