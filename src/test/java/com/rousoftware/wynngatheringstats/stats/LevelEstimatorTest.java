package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class LevelEstimatorTest {
    @Test
    void roundsNodesUpAndUsesWholeNodesForTime() {
        LevelEstimate estimate =
                LevelEstimator.estimate(1000, OptionalDouble.of(300), OptionalDouble.of(7.5));

        assertEquals(4, estimate.nodes().orElseThrow());
        assertEquals(30, estimate.seconds().orElseThrow(), 0.0001);
    }

    @Test
    void zeroRemainingXpProducesZeroNodesAndTime() {
        LevelEstimate estimate =
                LevelEstimator.estimate(0, OptionalDouble.of(100), OptionalDouble.of(5));

        assertEquals(0, estimate.nodes().orElseThrow());
        assertEquals(0, estimate.seconds().orElseThrow(), 0.0001);
    }

    @Test
    void leavesTimeUnavailableUntilTimingSamplesExist() {
        LevelEstimate estimate =
                LevelEstimator.estimate(1000, OptionalDouble.of(250), OptionalDouble.empty());

        assertEquals(4, estimate.nodes().orElseThrow());
        assertTrue(estimate.seconds().isEmpty());
    }

    @Test
    void rejectsMissingOrInvalidXpRates() {
        assertEquals(
                LevelEstimate.UNAVAILABLE,
                LevelEstimator.estimate(1000, OptionalDouble.empty(), OptionalDouble.of(5)));
        assertEquals(
                LevelEstimate.UNAVAILABLE,
                LevelEstimator.estimate(1000, OptionalDouble.of(0), OptionalDouble.of(5)));
        assertEquals(
                LevelEstimate.UNAVAILABLE,
                LevelEstimator.estimate(-1, OptionalDouble.of(100), OptionalDouble.of(5)));
    }
}
