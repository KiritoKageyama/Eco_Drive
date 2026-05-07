package com.ecodrive.ml;

public class TripTelemetry {
    // Real-time telemetry inputs derived from GPS, APIs, and user context
    public final double speedDiffMss; // Acceleration-derived Delta V proxy (m/s²)
    public final double trafficCongestionRatio; // 0.0 to 1.0 mapping from API
    public final boolean isAcOn;
    public final long timeStampMs;
    public final String terrainType;
    public final double routeAverageSpeedKmh;

    public TripTelemetry(double speedDiffMss, double trafficCongestionRatio, boolean isAcOn, long timeStampMs,
            String terrainType, double routeAverageSpeedKmh) {
        this.speedDiffMss = speedDiffMss;
        this.trafficCongestionRatio = trafficCongestionRatio;
        this.isAcOn = isAcOn;
        this.timeStampMs = timeStampMs;
        this.terrainType = terrainType;
        this.routeAverageSpeedKmh = routeAverageSpeedKmh;
    }
}
