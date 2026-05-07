package com.ecodrive.data;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TripEntity synthetic data handling.
 * Validates that synthetic (demo) trips are properly flagged and isolated.
 */
public class TripEntityTest {

    private TripEntity realTrip;
    private TripEntity syntheticTrip;

    @Before
    public void setUp() {
        long timestamp = System.currentTimeMillis();

        // Real trip from live tracking
        realTrip = new TripEntity(
                timestamp,
                25.5,           // distanceKm
                6.2,            // totalCo2Kg
                4.1,            // baseCo2Kg
                120.0,          // idleTimeSec
                "Bangalore Central",
                "Bangalore Tech Park"
                // Note: isSynthetic defaults to false
        );

        // Synthetic demo trip
        syntheticTrip = new TripEntity(
                timestamp,
                15.0,           // distanceKm
                3.1,            // totalCo2Kg
                2.3,            // baseCo2Kg
                60.0,           // idleTimeSec
                "Synthetic Demo Origin",
                "Synthetic Demo Destination",
                true            // IS synthetic
        );
    }

    @Test
    public void testRealTripNotMarkedSynthetic() {
        assertFalse("Real trip should not be marked synthetic", realTrip.isSynthetic);
    }

    @Test
    public void testSyntheticTripMarkedCorrectly() {
        assertTrue("Synthetic trip should be marked as synthetic", syntheticTrip.isSynthetic);
    }

    @Test
    public void testDefaultConstructorSynthenticFalse() {
        TripEntity trip = new TripEntity(
                System.currentTimeMillis(),
                10.0,
                2.5,
                1.8,
                45.0,
                "Start",
                "End"
        );
        assertFalse("Default constructor should set isSynthetic=false", trip.isSynthetic);
    }

    @Test
    public void testSyntheticFlagCanBeSet() {
        TripEntity demoTrip = new TripEntity(
                System.currentTimeMillis(),
                5.0,
                1.2,
                0.9,
                20.0,
                "Demo Start",
                "Demo End",
                true
        );
        assertTrue("Synthetic flag should be settable to true", demoTrip.isSynthetic);
    }

    @Test
    public void testRealTripDataIntegrity() {
        assertEquals("Distance should be preserved", 25.5, realTrip.distanceKm, 0.01);
        assertEquals("CO2 should be preserved", 6.2, realTrip.totalCo2Kg, 0.01);
        assertEquals("Base CO2 should be preserved", 4.1, realTrip.baseCo2Kg, 0.01);
        assertEquals("Idle time should be preserved", 120.0, realTrip.idleTimeSec, 0.01);
    }

    @Test
    public void testSyntheticTripDataIntegrity() {
        assertEquals("Distance should be preserved", 15.0, syntheticTrip.distanceKm, 0.01);
        assertEquals("CO2 should be preserved", 3.1, syntheticTrip.totalCo2Kg, 0.01);
        assertEquals("Idle time should be preserved", 60.0, syntheticTrip.idleTimeSec, 0.01);
    }

    @Test
    public void testLocationNamesPreserved() {
        assertEquals("Start location should match", "Bangalore Central", realTrip.startLocation);
        assertEquals("End location should match", "Bangalore Tech Park", realTrip.endLocation);

        assertEquals("Synthetic trip start should match", "Synthetic Demo Origin", syntheticTrip.startLocation);
        assertEquals("Synthetic trip end should match", "Synthetic Demo Destination", syntheticTrip.endLocation);
    }

    @Test
    public void testSyntheticLocationIdentifier() {
        // Synthetic trips should have identifiable location names
        assertTrue("Synthetic trip should have 'Synthetic' in origin", 
                syntheticTrip.startLocation.contains("Synthetic"));
        assertTrue("Synthetic trip should have 'Synthetic' in destination", 
                syntheticTrip.endLocation.contains("Synthetic"));
    }

    @Test
    public void testCo2CalculationConsistent() {
        // totalCo2Kg should always be >= baseCo2Kg
        assertTrue("Total CO2 should be >= base CO2", realTrip.totalCo2Kg >= realTrip.baseCo2Kg);
        assertTrue("Total CO2 should be >= base CO2 (synthetic)", syntheticTrip.totalCo2Kg >= syntheticTrip.baseCo2Kg);
    }

    @Test
    public void testIdleTimeNonNegative() {
        assertTrue("Idle time must be non-negative", realTrip.idleTimeSec >= 0);
        assertTrue("Idle time must be non-negative (synthetic)", syntheticTrip.idleTimeSec >= 0);
    }

    @Test
    public void testDistanceNonNegative() {
        assertTrue("Distance must be non-negative", realTrip.distanceKm > 0);
        assertTrue("Distance must be non-negative (synthetic)", syntheticTrip.distanceKm > 0);
    }

    @Test
    public void testTimestampCapture() {
        assertTrue("Timestamp should be positive", realTrip.timestampMs > 0);
        assertTrue("Timestamp should be positive (synthetic)", syntheticTrip.timestampMs > 0);
    }

    @Test
    public void testMultipleSyntheticTripsDistinguishable() {
        TripEntity demo1 = new TripEntity(System.currentTimeMillis(), 10.0, 2.3, 1.5, 30, "Demo1", "Demo1End", true);
        TripEntity demo2 = new TripEntity(System.currentTimeMillis(), 15.0, 3.2, 2.0, 45, "Demo2", "Demo2End", true);

        assertTrue("Demo1 should be synthetic", demo1.isSynthetic);
        assertTrue("Demo2 should be synthetic", demo2.isSynthetic);
        
        // Both marked as synthetic, but they're different trips
        assertEquals("Both trips should be synthetic", demo1.isSynthetic, demo2.isSynthetic);
        assertNotEquals("But have different distances", demo1.distanceKm, demo2.distanceKm, 0.01);
    }

    @Test
    public void testAutoIncrementIDNotSet() {
        // When creating via constructor, id should default to 0 (will auto-increment in DB)
        assertEquals("ID should default to 0 before database insert", 0, realTrip.id);
        assertEquals("ID should default to 0 before database insert (synthetic)", 0, syntheticTrip.id);
    }

}
