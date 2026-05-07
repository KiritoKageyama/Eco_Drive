package com.ecodrive.ml;

import com.ecodrive.physics.PhysicsFeatureSet;
import com.ecodrive.physics.TripResult;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for EmissionsMLModel class.
 * Validates physics-telemetry fusion, CO2 calculations, and synthetic flag handling.
 */
public class EmissionsMLModelTest {

    private EmissionsMLModel mlModel;
    private List<PhysicsFeatureSet> mockPhysicsFeatures;
    private TripTelemetry mockTelemetry;

    private PhysicsFeatureSet buildPhysicsFeature(boolean isEV, double baseCo2Kg, double rawFuelLitres,
            double requiredMechanicalEnergyJ) {
        return new PhysicsFeatureSet(
                requiredMechanicalEnergyJ,
                rawFuelLitres,
                0.0,
                baseCo2Kg,
                10.0,
                isEV,
                "mock",
                0.32,
                0.01,
                0.92);
    }

    @Before
    public void setUp() {
        mlModel = new EmissionsMLModel();

        // Create mock physics features (simplified)
        mockPhysicsFeatures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            mockPhysicsFeatures.add(buildPhysicsFeature(false, 2.3 + (Math.random() * 0.5), 0.8, 5e6));
        }

        // Create mock telemetry (eco driving conditions)
        mockTelemetry = new TripTelemetry(
                0.3,        // low acceleration
                0.1,        // light traffic
                false,      // no AC
                System.currentTimeMillis(),
                "plains",   // flat terrain
                70.0        // highway speed
        );
    }

    @Test
    public void testRealTripNotMarkedSynthetic() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, false);
        assertFalse("Real trip should not be marked synthetic", result.isSynthetic);
    }

    @Test
    public void testSyntheticTripMarkedCorrectly() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, true);
        assertTrue("Synthetic trip should be marked as synthetic", result.isSynthetic);
    }

    @Test
    public void testCo2OutputPositive() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, false);
        assertTrue("CO2 output must be positive", result.meanCo2Kg > 0);
        assertTrue("Lower bound must be positive", result.lowerBoundCo2Kg >= 0);
        assertTrue("Upper bound must be positive", result.upperBoundCo2Kg > 0);
    }

    @Test
    public void testCo2BoundsConsistent() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, false);
        assertTrue("Lower bound <= mean", result.lowerBoundCo2Kg <= result.meanCo2Kg);
        assertTrue("Mean <= upper bound", result.meanCo2Kg <= result.upperBoundCo2Kg);
    }

    @Test
    public void testACPenaltyIncreasesCo2() {
        TripTelemetry withoutAC = new TripTelemetry(0.3, 0.1, false, System.currentTimeMillis(), "plains", 70.0);
        TripTelemetry withAC = new TripTelemetry(0.3, 0.1, true, System.currentTimeMillis(), "plains", 70.0);

        TripResult resultNoAC = mlModel.predictTripEmissions(mockPhysicsFeatures, withoutAC, false);
        TripResult resultWithAC = mlModel.predictTripEmissions(mockPhysicsFeatures, withAC, false);

        assertTrue("AC should increase CO2 emissions", resultWithAC.meanCo2Kg > resultNoAC.meanCo2Kg);
    }

    @Test
    public void testTerrainMultiplierAffectsCo2() {
        TripTelemetry flatTerrain = new TripTelemetry(0.3, 0.1, false, System.currentTimeMillis(), "plains", 70.0);
        TripTelemetry hillyTerrain = new TripTelemetry(0.3, 0.1, false, System.currentTimeMillis(), "hilly", 70.0);

        TripResult resultFlat = mlModel.predictTripEmissions(mockPhysicsFeatures, flatTerrain, false);
        TripResult resultHilly = mlModel.predictTripEmissions(mockPhysicsFeatures, hillyTerrain, false);

        // Hilly terrain should increase CO2 (approximately 15% multiplier)
        assertTrue("Hilly terrain should increase CO2 vs flat", 
                resultHilly.meanCo2Kg > resultFlat.meanCo2Kg * 1.05);
    }

    @Test
    public void testAggressiveDrivingIncreasesEmissions() {
        TripTelemetry ecoDriving = new TripTelemetry(0.2, 0.1, false, System.currentTimeMillis(), "plains", 70.0);
        TripTelemetry aggressiveDriving = new TripTelemetry(2.0, 0.3, true, System.currentTimeMillis(), "plains", 70.0);

        TripResult resultEco = mlModel.predictTripEmissions(mockPhysicsFeatures, ecoDriving, false);
        TripResult resultAggressive = mlModel.predictTripEmissions(mockPhysicsFeatures, aggressiveDriving, false);

        assertTrue("Aggressive driving should increase emissions", 
                resultAggressive.meanCo2Kg > resultEco.meanCo2Kg);
    }

    @Test
    public void testHighTrafficIncreasesEmissions() {
        TripTelemetry lightTraffic = new TripTelemetry(0.3, 0.05, false, System.currentTimeMillis(), "urban", 70.0);
        TripTelemetry heavyTraffic = new TripTelemetry(0.5, 0.8, true, System.currentTimeMillis(), "urban", 15.0);

        TripResult resultLight = mlModel.predictTripEmissions(mockPhysicsFeatures, lightTraffic, false);
        TripResult resultHeavy = mlModel.predictTripEmissions(mockPhysicsFeatures, heavyTraffic, false);

        assertTrue("Heavy traffic should increase emissions", 
                resultHeavy.meanCo2Kg > resultLight.meanCo2Kg);
    }

    @Test
    public void testFuelOutputConsistent() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, false);
        assertTrue("Fuel output must be positive", result.meanFuelLitres > 0);
        assertTrue("Fuel should be realistic (not >10L for 10km)", result.meanFuelLitres < 10.0);
    }

    @Test
    public void testEVPathwayZeroFuel() {
        // Create EV physics features
        List<PhysicsFeatureSet> evFeatures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            evFeatures.add(buildPhysicsFeature(true, 1.8, 0.0, 4e6));
        }

        TripResult result = mlModel.predictTripEmissions(evFeatures, mockTelemetry, false);
        assertTrue("EV should have zero fuel", result.meanFuelLitres == 0.0);
        assertTrue("EV should have lower CO2", result.meanCo2Kg < 2.0);
    }

    @Test
    public void testRecommendationsGenerated() {
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, mockTelemetry, false);
        assertNotNull("Recommendations must not be null", result.recommendations);
        assertTrue("Recommendations must not be empty", result.recommendations.length() > 0);
    }

    @Test
    public void testRecommendationsForAggressiveDriving() {
        TripTelemetry aggressive = new TripTelemetry(2.5, 0.1, false, System.currentTimeMillis(), "plains", 70.0);
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, aggressive, false);
        assertTrue("Should recommend reducing acceleration for aggressive driving", 
                result.recommendations.toLowerCase().contains("acceleration"));
    }

    @Test
    public void testRecommendationsForHeavyTraffic() {
        TripTelemetry traffic = new TripTelemetry(0.3, 0.7, false, System.currentTimeMillis(), "urban", 20.0);
        TripResult result = mlModel.predictTripEmissions(mockPhysicsFeatures, traffic, false);
        assertTrue("Should recommend avoiding traffic",
                result.recommendations.toLowerCase().contains("route") || 
                result.recommendations.toLowerCase().contains("traffic"));
    }

}
