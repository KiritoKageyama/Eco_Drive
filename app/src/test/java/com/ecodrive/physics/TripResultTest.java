package com.ecodrive.physics;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TripResult class.
 * Validates synthetic flag handling and result formatting.
 */
public class TripResultTest {

    private TripResult realTrip;
    private TripResult syntheticTrip;

    @Before
    public void setUp() {
        // Real trip data
        realTrip = new TripResult(
                2.5,    // meanCo2Kg
                2.0,    // lowerBoundCo2Kg
                3.2,    // upperBoundCo2Kg
                0.85,   // meanFuelLitres
                8.5,    // energyReqMj
                false,  // isEV
                "Optimal driving behavior",
                false   // NOT synthetic
        );

        // Synthetic demo trip data
        syntheticTrip = new TripResult(
                3.1,    // meanCo2Kg
                2.4,    // lowerBoundCo2Kg
                4.0,    // upperBoundCo2Kg
                1.1,    // meanFuelLitres
                11.0,   // energyReqMj
                false,  // isEV
                "Reduce harsh acceleration",
                true    // IS synthetic
        );
    }

    @Test
    public void testSyntheticFlagFalseForRealTrip() {
        assertFalse("Real trip should have isSynthetic=false", realTrip.isSynthetic);
    }

    @Test
    public void testSyntheticFlagTrueForDemoTrip() {
        assertTrue("Demo trip should have isSynthetic=true", syntheticTrip.isSynthetic);
    }

    @Test
    public void testCo2ValuesAreValid() {
        assertTrue("Mean CO2 must be positive", realTrip.meanCo2Kg > 0);
        assertTrue("Lower bound must be <= mean", realTrip.lowerBoundCo2Kg <= realTrip.meanCo2Kg);
        assertTrue("Mean must be <= upper bound", realTrip.meanCo2Kg <= realTrip.upperBoundCo2Kg);
    }

    @Test
    public void testFuelValuesAreValid() {
        assertTrue("Mean fuel must be positive", realTrip.meanFuelLitres > 0);
        assertTrue("Energy requirement must be positive", realTrip.energyReqMj > 0);
    }

    @Test
    public void testEVPathwayDistinguishable() {
        TripResult evTrip = new TripResult(
                1.8,    // lower CO2 for EV
                1.2,
                2.4,
                0.0,    // no fuel
                7.2,    // kWh in MJ
                true,   // IS EV
                "EV optimal",
                false
        );
        assertTrue("EV flag must be true", evTrip.isEV);
        assertTrue("EV fuel should be zero", evTrip.meanFuelLitres == 0.0);
    }

    @Test
    public void testFormatSummaryForICE() {
        String summary = realTrip.formatSummary();
        assertNotNull("Summary must not be null", summary);
        assertTrue("ICE summary must contain CO2", summary.contains("CO2"));
        assertTrue("ICE summary must contain Fuel", summary.contains("Fuel"));
    }

    @Test
    public void testFormatSummaryForEV() {
        TripResult evTrip = new TripResult(1.8, 1.2, 2.4, 0.0, 7.2, true, "Eco EV mode", false);
        String summary = evTrip.formatSummary();
        assertNotNull("Summary must not be null", summary);
        assertTrue("EV summary must contain CO2", summary.contains("CO2"));
        assertTrue("EV summary must contain Energy (kWh)", summary.contains("kWh"));
    }

    @Test
    public void testUncertaintyCalculation() {
        // Tight bounds
        TripResult tightBounds = new TripResult(2.0, 1.9, 2.1, 0.7, 7.0, false, "Good", false);
        String tightSummary = tightBounds.formatSummary();
        assertTrue("Tight bounds should show low uncertainty", tightSummary.contains("5.0%"));

        // Wide bounds
        TripResult wideBounds = new TripResult(2.0, 0.5, 4.5, 0.7, 7.0, false, "Uncertain", false);
        String wideSummary = wideBounds.formatSummary();
        assertTrue("Wide bounds should show high uncertainty", 
                wideSummary.contains("112") || wideSummary.contains("125")); // Rough bounds check
    }

    @Test
    public void testRecommendationsPresent() {
        assertNotNull("Recommendations must not be null", realTrip.recommendations);
        assertTrue("Recommendations must not be empty", realTrip.recommendations.length() > 0);
    }

}
