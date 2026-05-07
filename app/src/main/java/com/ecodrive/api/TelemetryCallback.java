package com.ecodrive.api;

public interface TelemetryCallback {
    /**
     * @param distanceKm total distance
     * @param durationSec total travel time
     * @param temperatureC current temperature
     * @param humidity percent
     * @param windSpeedKmh wind speed
     * @param weatherCondition e.g. "Cloudy", "Rain"
     * @param stopRatio traffic density proxy
     * @param terrainType e.g. "Hilly"
     * @param routeAverageSpeedKmh derived speed
     * @param geometryEncoded route line
     * @param majorRoadsSummary list of highways
     */
    void onSuccess(double distanceKm, double durationSec, double temperatureC, 
            double humidity, double windSpeedKmh, String weatherCondition,
            double stopRatio, String terrainType, double routeAverageSpeedKmh, 
            String geometryEncoded, String majorRoadsSummary);

    void onError(Exception e);
}
