package com.ecodrive.physics;

import java.io.Serializable;

public class VehicleSpecs implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Category {
        SEDAN, HATCHBACK, SUV
    }

    public enum FuelType {
        PETROL, DIESEL, EV, CNG
    }

    public String modelName;
    public double massKg;
    public double engineCc;
    public double frontalAreaM2;
    public double dragCoefficient;
    public boolean isEV;

    public Category category;
    public FuelType fuelType;
    public java.util.List<FuelType> allowedFuelTypes;

    public VehicleSpecs(String modelName, double massKg, double engineCc, double frontalAreaM2, double dragCoefficient,
            Category category, FuelType fuelType) {
        this.modelName = modelName;
        this.massKg = massKg;
        this.engineCc = engineCc;
        this.frontalAreaM2 = frontalAreaM2;
        this.dragCoefficient = dragCoefficient;
        this.isEV = (fuelType == FuelType.EV);
        this.category = category;
        this.fuelType = fuelType;
        this.allowedFuelTypes = new java.util.ArrayList<>();
        this.allowedFuelTypes.add(fuelType);
    }

    public VehicleSpecs(double massKg, double engineCc, double frontalAreaM2, double dragCoefficient, boolean isEV) {
        this.modelName = "Generic Vehicle";
        this.massKg = massKg;
        this.engineCc = engineCc;
        this.frontalAreaM2 = frontalAreaM2;
        this.dragCoefficient = dragCoefficient;
        this.isEV = isEV;
        this.category = Category.SEDAN; // default
        this.fuelType = isEV ? FuelType.EV : FuelType.PETROL; // default
        this.allowedFuelTypes = new java.util.ArrayList<>();
        this.allowedFuelTypes.add(this.fuelType);
    }

    public VehicleSpecs(double massKg, double engineCc, double frontalAreaM2, double dragCoefficient,
            Category category, FuelType fuelType) {
        this("Generic Vehicle", massKg, engineCc, frontalAreaM2, dragCoefficient, category, fuelType);
    }

    @Override
    public String toString() {
        return modelName != null ? modelName : "Unknown Vehicle";
    }
}
