package com.ecodrive.tracking;

import android.location.Location;

public class TripContextEngine {

    public enum VehicleState {
        DRIVING,
        IDLING,
        PREDICTED_OFF
    }

    public interface AutoEndListener {
        void onTripAutoEnded();
    }

    private VehicleState currentState = VehicleState.DRIVING;
    private long lastStateChangeTime = System.currentTimeMillis();
    private AutoEndListener autoEndListener;

    // Configurable thresholds
    private static final double IDLE_SPEED_THRESHOLD_KMH = 2.0;
    private static final long ENGINE_OFF_THRESHOLD_MS = 3 * 60 * 1000; // 3 minutes
    private static final long AUTO_END_THRESHOLD_MS = 60 * 60 * 1000; // 1 Hour

    private String currentAreaType = "unknown"; // e.g., residential, motorway
    private String currentTerrainType = "unknown";
    private double recentStopRatio = 0.0;
    private int nearbyTrafficSignals = 0;

    public void setAutoEndListener(AutoEndListener listener) {
        this.autoEndListener = listener;
    }

    /**
     * Core update loop called every time the GPS ticks.
     * Evaluates speed and time to determine the driving state.
     */
    public void updateState(Location location, double speedKmh) {
        long now = System.currentTimeMillis();

        if (speedKmh <= IDLE_SPEED_THRESHOLD_KMH) {
            // We are stopped or crawling
            if (currentState == VehicleState.DRIVING) {
                // Just stopped
                currentState = VehicleState.IDLING;
                lastStateChangeTime = now;
            } else if (currentState == VehicleState.IDLING || currentState == VehicleState.PREDICTED_OFF) {
                // We've been idling. Check if we should predict the engine is Cut-Off.
                long idleDuration = now - lastStateChangeTime;

                if (currentState == VehicleState.IDLING && idleDuration >= ENGINE_OFF_THRESHOLD_MS
                        && isLikelyEngineOffZone()) {
                    currentState = VehicleState.PREDICTED_OFF;
                }

                // If idling globally for > 1 Hour, end the trip entirely
                if (idleDuration >= AUTO_END_THRESHOLD_MS) {
                    if (autoEndListener != null) {
                        autoEndListener.onTripAutoEnded();
                    }
                }
            }
        } else {
            // We are moving
            if (currentState != VehicleState.DRIVING) {
                currentState = VehicleState.DRIVING;
                lastStateChangeTime = now;
            }
        }
    }

    /**
     * Determines if the current mapped area is likely to result in an engine
     * shutoff
     * rather than a traffic jam.
     */
    private boolean isLikelyEngineOffZone() {
        if (currentAreaType == null)
            return false;
        return currentAreaType.contains("residential") ||
                currentAreaType.contains("parking") ||
                currentAreaType.contains("service");
    }

    public void updateContext(String areaType, int trafficSignals) {
        this.currentAreaType = areaType;
        this.nearbyTrafficSignals = trafficSignals;
        this.currentTerrainType = inferTerrainType(areaType);

        // Dynamically estimate stop ratio based on local signals if driving
        if (trafficSignals > 0) {
            this.recentStopRatio = Math.min(1.0, (double) trafficSignals / 5.0); // Rough estimate
        } else {
            this.recentStopRatio = 0.0;
        }
    }

    private String inferTerrainType(String areaType) {
        if (areaType == null) {
            return "unknown";
        }

        String normalized = areaType.toLowerCase();
        if (normalized.contains("hill")) {
            return "hilly";
        }
        if (normalized.contains("motorway") || normalized.contains("primary") || normalized.contains("secondary")) {
            return "arterial";
        }
        if (normalized.contains("residential") || normalized.contains("commercial") || normalized.contains("industrial")) {
            return "urban";
        }
        if (normalized.contains("farmland") || normalized.contains("grass") || normalized.contains("forest")
                || normalized.contains("wood")) {
            return "plains";
        }
        if (normalized.contains("rolling")) {
            return "rolling";
        }
        return "unknown";
    }

    public VehicleState getCurrentState() {
        return currentState;
    }

    public String getCurrentAreaType() {
        return currentAreaType;
    }

    public String getCurrentTerrainType() {
        return currentTerrainType;
    }

    public double getDynamicStopRatio() {
        return recentStopRatio;
    }

    public double getDynamicRoadGradePercent() {
        if (currentTerrainType == null) {
            return 0.0;
        }

        switch (currentTerrainType) {
            case "hilly":
                return 6.0;
            case "rolling":
                return 3.0;
            case "urban":
                return 1.5;
            case "arterial":
                return 0.8;
            case "plains":
                return 0.3;
            default:
                return 0.0;
        }
    }

    public boolean isEngineOff() {
        return currentState == VehicleState.PREDICTED_OFF;
    }
}
