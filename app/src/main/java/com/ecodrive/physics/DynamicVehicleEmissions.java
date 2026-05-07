package com.ecodrive.physics;

import java.util.Arrays;
import java.util.Random;

public class DynamicVehicleEmissions {

    // Standard Constants
    private static final double G = 9.81;

    // Calorific values
    private static final double CV_PETROL_MJ_L = 32.0;
    private static final double CV_DIESEL_MJ_L = 36.0;
    private static final double CV_CNG_MJ_KG = 47.7; // MJ/kg for CNG

    // Emission Factors
    private static final double EF_PETROL = 2.31; // kg CO2 / L
    private static final double EF_DIESEL = 2.68; // kg CO2 / L
    private static final double EF_CNG = 2.75; // kg CO2 / kg

    // Grid Intensity for EV (kg CO2 / kWh) - Indian Average approx
    private static final double GRID_INTENSITY_INDIA = 0.716;

    /**
     * Phase 0 Baseline Physics Implementation
     * Generates purely physical raw features without ML/Traffic multipliers.
     * Represents the "Continuous Value from Physics" block in the flowchart.
     */
    public static java.util.List<PhysicsFeatureSet> generatePhysicsFeatures(
            VehicleSpecs specs,
            double distanceKm,
            double averageSpeedKmh,
            double accelerationMss,
            double roadGradePercent,
            double airDensityKgM3,
            int iterations) {

        double vMs = averageSpeedKmh / 3.6;
        if (vMs <= 0.1)
            vMs = 0.1; // avoid division by zero
        double timeS = (distanceKm * 1000.0) / vMs;

        java.util.List<PhysicsFeatureSet> featureSets = new java.util.ArrayList<>();
        Random rand = new Random();

        // Deterministic Vehicle Parameters
        double crr = 0.009 + Math.min(specs.massKg / 3000.0, 1.0) * (0.013 - 0.009);

        double cd = 0.28; // Sedan default
        if (specs.category == VehicleSpecs.Category.HATCHBACK)
            cd = 0.32;
        else if (specs.category == VehicleSpecs.Category.SUV)
            cd = 0.36;

        double etaEng = 0.25;
        if (specs.fuelType == VehicleSpecs.FuelType.PETROL || specs.fuelType == VehicleSpecs.FuelType.CNG) {
            etaEng = 0.22 + (1.5 / Math.max(specs.engineCc / 1000.0, 1.0)) * 0.03;
        } else if (specs.fuelType == VehicleSpecs.FuelType.DIESEL) {
            etaEng = 0.32 + (1.5 / Math.max(specs.engineCc / 1000.0, 1.0)) * 0.03;
        } else { // EV
            etaEng = 0.90;
        }

        for (int i = 0; i < iterations; i++) {
            // 1. Sample Environmental Uncertainties (Real-world Monte Carlo)
            double iterVMs = Math.max(vMs + rand.nextGaussian() * 1.5, 0.1);
            double iterGrade = roadGradePercent + rand.nextGaussian() * 0.5;
            double headwindMs = rand.nextGaussian() * 2.0; // +/- wind
            double vApparent = iterVMs + headwindMs;

            // 2. Base Force Calculations (Formula 3.1)
            double fRoll = specs.massKg * G * crr;
            double fAero = 0.5 * airDensityKgM3 * specs.frontalAreaM2 * cd * vApparent * Math.abs(vApparent);

            // Grade resistance
            double theta = Math.atan(iterGrade / 100.0);
            double fGrade = specs.massKg * G * Math.sin(theta);

            double fAcc = specs.massKg * accelerationMss;

            double fTotal = fRoll + fAero + fGrade + fAcc;
            if (fTotal < 0)
                fTotal = 0; // Simplified regening assumption

            // 3. Power and Energy Demand (Mechanical)
            double powerReqW = fTotal * iterVMs;
            double energyReqJ = powerReqW * timeS;

            // 4. Base Fuel & CO2 Calculation (WITHOUT Traffic/AC multipliers yet - handled
            // by ML layer)
            double rawFuelLitres = 0.0;
            double rawEvKwh = 0.0;
            double baseCo2Kg = 0.0;

            double fuelEnergyJ = energyReqJ / etaEng;

            if (specs.fuelType == VehicleSpecs.FuelType.EV) {
                rawEvKwh = fuelEnergyJ / 3600000.0;
                baseCo2Kg = rawEvKwh * GRID_INTENSITY_INDIA; // Base Grid CO2
            } else if (specs.fuelType == VehicleSpecs.FuelType.CNG) {
                rawFuelLitres = (fuelEnergyJ / 1000000.0) / CV_CNG_MJ_KG; // Measured in kg for CNG
                baseCo2Kg = rawFuelLitres * EF_CNG;
            } else {
                double cvMjL = (specs.fuelType == VehicleSpecs.FuelType.PETROL) ? CV_PETROL_MJ_L : CV_DIESEL_MJ_L;
                rawFuelLitres = (fuelEnergyJ / 1000000.0) / cvMjL;

                double ef = (specs.fuelType == VehicleSpecs.FuelType.PETROL) ? EF_PETROL : EF_DIESEL;
                baseCo2Kg = rawFuelLitres * ef; // Base tailpipe CO2
            }

            featureSets.add(new PhysicsFeatureSet(
                    energyReqJ, rawFuelLitres, rawEvKwh, baseCo2Kg, distanceKm, specs.isEV, specs.modelName,
                    cd, crr, etaEng));
        }

        return featureSets;
    }
}
