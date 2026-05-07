package com.ecodrive.data;

import android.content.Context;
import android.util.Log;

import com.ecodrive.physics.VehicleSpecs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class VehicleDatabase {

    private static final List<VehicleSpecs> loadedDataset = new ArrayList<>();
    private static boolean isInitialized = false;

    /**
     * Reads the final_cars_dataset.csv from the app's assets folder.
     */
    public static void init(Context context) {
        if (isInitialized)
            return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("final_cars_dataset.csv")))) {

            String line;
            boolean isFirstRow = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstRow) {
                    isFirstRow = false; // skip header (Brand,Car_Name,Price,Rating,Safety,Mileage,Power,Sales)
                    continue;
                }

                String[] tokens = line.split(",", -1);
                if (tokens.length >= 2) {
                    String brand = tokens[0].trim();
                    String carName = tokens[1].trim();
                    String powerStr = tokens.length > 6 ? tokens[6].trim() : "";

                    // Defaults for variables not explicitly in the CSV dataset
                    double massKg = 1200.0;
                    double engineCc = 1200.0;
                    double frontalAreaM2 = 2.2;
                    double dragCoefficient = 0.32;

                    VehicleSpecs.Category category = VehicleSpecs.Category.SEDAN;
                    VehicleSpecs.FuelType fuelType = VehicleSpecs.FuelType.PETROL;

                    String lowercaseName = carName.toLowerCase();

                    // Rough heuristics to map CSV strings into physical vehicle bounds
                    if (lowercaseName.contains("ev") || lowercaseName.contains("electric")
                            || lowercaseName.contains("eq")) {
                        fuelType = VehicleSpecs.FuelType.EV;
                        massKg += 300.0; // Batteries are heavy
                    } else if (lowercaseName.contains("diesel") || brand.equalsIgnoreCase("Mahindra")
                            || lowercaseName.contains("fortuner")) {
                        fuelType = VehicleSpecs.FuelType.DIESEL;
                    }

                    if (lowercaseName.contains("suv") || lowercaseName.contains("rover")
                            || brand.equalsIgnoreCase("Mahindra")) {
                        category = VehicleSpecs.Category.SUV;
                        massKg = 1600.0;
                        frontalAreaM2 = 2.6;
                        dragCoefficient = 0.38;
                        engineCc = 1500.0;
                    } else if (lowercaseName.contains("swift") || lowercaseName.contains("i20")
                            || lowercaseName.contains("alto") || lowercaseName.contains("tiago")) {
                        category = VehicleSpecs.Category.HATCHBACK;
                        massKg = 900.0;
                        frontalAreaM2 = 2.0;
                        dragCoefficient = 0.30;
                        engineCc = 1000.0;
                    }

                    // Map Power (BHP) exactly to dataset value to derive engine physics
                    if (!powerStr.isEmpty()) {
                        try {
                            String cleanPower = powerStr.replaceAll("[^0-9.]", "");
                            if (!cleanPower.isEmpty()) {
                                double bhp = Double.parseDouble(cleanPower);
                                engineCc = Math.max(800.0, bhp * 11.5); // Standard BHP/CC volumetric efficiency
                                // Mathematically bound mass based on power-to-weight ratios
                                massKg = Math.min(2500.0, Math.max(800.0, bhp * 12.0));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // AI/Mathematical Fallback for Aerodynamics (since exact Cd is missing in CSV)
                    // Scales theoretically based on derived mass and category dimensions
                    if (category == VehicleSpecs.Category.SUV) {
                        frontalAreaM2 = 2.4 + Math.max(0, massKg - 1000) * 0.0005;
                        dragCoefficient = 0.35 + Math.max(0, massKg - 1000) * 0.00002;
                    } else if (category == VehicleSpecs.Category.HATCHBACK) {
                        frontalAreaM2 = 1.8 + Math.max(0, massKg - 800) * 0.0004;
                        dragCoefficient = 0.28 + Math.max(0, massKg - 800) * 0.00002;
                    } else { // SEDAN
                        frontalAreaM2 = 2.0 + Math.max(0, massKg - 1000) * 0.0004;
                        dragCoefficient = 0.25 + Math.max(0, massKg - 1000) * 0.00002;
                    }

                    VehicleSpecs newSpec = new VehicleSpecs(carName, massKg, engineCc, frontalAreaM2, dragCoefficient,
                            category, fuelType);

                    // Add constraints: EVs are strict. ICE vehicles might support CNG/Diesel.
                    if (fuelType != VehicleSpecs.FuelType.EV) {
                        if (category == VehicleSpecs.Category.HATCHBACK) {
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.PETROL))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.PETROL);
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.CNG))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.CNG);
                        } else if (category == VehicleSpecs.Category.SUV) {
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.PETROL))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.PETROL);
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.DIESEL))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.DIESEL);
                        } else {
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.PETROL))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.PETROL);
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.DIESEL))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.DIESEL);
                            if (!newSpec.allowedFuelTypes.contains(VehicleSpecs.FuelType.CNG))
                                newSpec.allowedFuelTypes.add(VehicleSpecs.FuelType.CNG);
                        }
                    }

                    loadedDataset.add(newSpec);
                }
            }
            isInitialized = true;

        } catch (Exception e) {
            Log.e("VehicleDatabase", "Error parsing dataset CSV", e);
        }
    }

    public static List<VehicleSpecs> getAllVehicles() {
        return loadedDataset;
    }

    public static VehicleSpecs getVehicleByName(String name) {
        for (VehicleSpecs specs : loadedDataset) {
            if (specs.modelName.equalsIgnoreCase(name)) {
                return specs;
            }
        }
        return loadedDataset.isEmpty() ? null : loadedDataset.get(0);
    }
}
