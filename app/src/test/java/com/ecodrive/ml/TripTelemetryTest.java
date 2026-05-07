package com.ecodrive.ml;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TripTelemetry class.
 * Validates terrain classification and telemetry data integrity.
 */
public class TripTelemetryTest {

    private TripTelemetry telemetry;

    @Before
    public void setUp() {
        telemetry = new TripTelemetry(
                1.5,                    // speedDiffMss (acceleration magnitude)
                0.25,                   // trafficCongestionRatio
                true,                   // isAcOn
                System.currentTimeMillis(),  // timestamp
                "rolling",              // terrainType
                45.0                    // routeAverageSpeedKmh
        );
    }

    @Test
    public void testTelemetryInitialization() {
        assertEquals(1.5, telemetry.speedDiffMss, 0.01);
        assertEquals(0.25, telemetry.trafficCongestionRatio, 0.01);
        assertTrue(telemetry.isAcOn);
        assertEquals("rolling", telemetry.terrainType);
        assertEquals(45.0, telemetry.routeAverageSpeedKmh, 0.01);
    }

    @Test
    public void testSpeedDiffValidRange() {
        assertTrue("Speed diff must be non-negative", telemetry.speedDiffMss >= 0);
        // Reasonable upper bound: 5 m/s^2 = moderate-to-aggressive acceleration
        assertTrue("Speed diff should be realistic", telemetry.speedDiffMss < 10.0);
    }

    @Test
    public void testTrafficCongestionValidRange() {
        assertTrue("Traffic ratio must be [0,1]", telemetry.trafficCongestionRatio >= 0);
        assertTrue("Traffic ratio must be [0,1]", telemetry.trafficCongestionRatio <= 1.0);
    }

    @Test
    public void testTerrainTypeClassification() {
        // Test valid terrain types
        String[] validTerrains = {"hilly", "rolling", "plains", "urban", "residential", "arterial"};
        for (String terrain : validTerrains) {
            TripTelemetry test = new TripTelemetry(0.5, 0.1, false, System.currentTimeMillis(), terrain, 50.0);
            assertNotNull("Terrain must not be null", test.terrainType);
            assertTrue("Terrain should be recognized", test.terrainType.length() > 0);
        }
    }

    @Test
    public void testHillyTerrainIdentified() {
        TripTelemetry hillyTrip = new TripTelemetry(0.5, 0.1, false, System.currentTimeMillis(), "hilly", 30.0);
        assertEquals("Hilly terrain should be preserved", "hilly", hillyTrip.terrainType);
    }

    @Test
    public void testRouteSpeedValidRange() {
        assertTrue("Route speed must be non-negative", telemetry.routeAverageSpeedKmh >= 0);
        // Reasonable upper bound: highway speeds ~200 km/h
        assertTrue("Route speed should be realistic", telemetry.routeAverageSpeedKmh < 250.0);
    }

    @Test
    public void testACToggle() {
        TripTelemetry noAC = new TripTelemetry(0.5, 0.2, false, System.currentTimeMillis(), "plains", 60.0);
        assertFalse("AC should be off", noAC.isAcOn);

        TripTelemetry withAC = new TripTelemetry(0.5, 0.2, true, System.currentTimeMillis(), "plains", 60.0);
        assertTrue("AC should be on", withAC.isAcOn);
    }

    @Test
    public void testLowTrafficScenario() {
        TripTelemetry easyDriving = new TripTelemetry(0.2, 0.05, false, System.currentTimeMillis(), "plains", 80.0);
        assertEquals("Low traffic scenario should have low congestion", 0.05, easyDriving.trafficCongestionRatio, 0.01);
    }

    @Test
    public void testHighTrafficScenario() {
        TripTelemetry heavyTraffic = new TripTelemetry(0.3, 0.8, true, System.currentTimeMillis(), "urban", 15.0);
        assertEquals("Heavy traffic should have high congestion", 0.8, heavyTraffic.trafficCongestionRatio, 0.01);
        assertEquals("Urban terrain for traffic", "urban", heavyTraffic.terrainType);
    }

    @Test
    public void testTimestampCapture() {
        long beforeTime = System.currentTimeMillis();
        TripTelemetry newTrip = new TripTelemetry(0.5, 0.1, false, System.currentTimeMillis(), "rolling", 50.0);
        long afterTime = System.currentTimeMillis();

        assertTrue("Timestamp should be between before and after", 
                newTrip.timeStampMs >= beforeTime && newTrip.timeStampMs <= afterTime);
    }

}
