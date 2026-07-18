package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CombatStatsTrackerTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void calculatesRequestedCombatRatesFromWynntilsObservations() {
        CombatStatsTracker tracker = tracker(20);

        tracker.recordXpGain(100, 0);
        tracker.recordKill(100, 0);
        tracker.recordItemPickup(2, SECOND);
        tracker.recordXpGain(300, 10 * SECOND);
        tracker.recordKill(300, 10 * SECOND);
        tracker.recordItemPickup(1, 11 * SECOND);

        CombatTrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(72_000, snapshot.xpPerHour().orElseThrow(), 0.0001);
        assertEquals(540, snapshot.itemsPerHour().orElseThrow(), 0.0001);
        assertEquals(200, snapshot.xpPerKill().orElseThrow(), 0.0001);
        assertEquals(360, snapshot.killsPerHour().orElseThrow(), 0.0001);
    }

    @Test
    void keepsPickupsThatArriveBeforeTheFirstKillLabel() {
        CombatStatsTracker tracker = tracker(20);

        tracker.recordItemPickup(3, 0);
        tracker.recordKill(50, SECOND);
        tracker.recordKill(50, 11 * SECOND);

        assertEquals(540, tracker.snapshot().itemsPerHour().orElseThrow(), 0.0001);
    }

    @Test
    void derivesXpPerHourFromKillLabelsWhenTimedXpEventsAreUnavailable() {
        CombatStatsTracker tracker = tracker(20);

        tracker.recordKill(100, 0);
        tracker.recordKill(300, 10 * SECOND);

        CombatTrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(72_000, snapshot.xpPerHour().orElseThrow(), 0.0001);
        assertEquals(200, snapshot.xpPerKill().orElseThrow(), 0.0001);
        assertEquals(360, snapshot.killsPerHour().orElseThrow(), 0.0001);
        assertEquals(0, snapshot.xpSampleCount());
    }

    @Test
    void prefersDirectXpEventsWhenTheirRateIsAvailable() {
        CombatStatsTracker tracker = tracker(20);

        tracker.recordKill(100, 0);
        tracker.recordXpGain(200, 0);
        tracker.recordKill(100, 10 * SECOND);
        tracker.recordXpGain(400, 10 * SECOND);

        assertEquals(108_000, tracker.snapshot().xpPerHour().orElseThrow(), 0.0001);
    }

    @Test
    void ratesNeedTwoTimedSamplesAndUseTheRollingWindow() {
        CombatStatsTracker tracker = tracker(2);

        tracker.recordXpGain(10, 0);
        tracker.recordKill(10, 0);
        assertTrue(tracker.snapshot().xpPerHour().isEmpty());
        assertTrue(tracker.snapshot().killsPerHour().isEmpty());

        tracker.recordXpGain(20, 10 * SECOND);
        tracker.recordKill(20, 10 * SECOND);
        tracker.recordXpGain(30, 30 * SECOND);
        tracker.recordKill(30, 30 * SECOND);

        CombatTrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(6_000, snapshot.xpPerHour().orElseThrow(), 0.0001);
        assertEquals(240, snapshot.killsPerHour().orElseThrow(), 0.0001);
        assertEquals(25, snapshot.xpPerKill().orElseThrow(), 0.0001);
        assertEquals(2, snapshot.xpSampleCount());
        assertEquals(2, snapshot.killSampleCount());
    }

    @Test
    void idleTimeoutClearsTheCombatSession() {
        CombatStatsTracker tracker = tracker(20);
        tracker.recordXpGain(100, 0);
        tracker.recordKill(100, 0);

        tracker.update(300 * SECOND);

        assertFalse(tracker.snapshot().active());
    }

    @Test
    void inactivityWindowCanBeReconfiguredWithoutResettingImmediately() {
        CombatStatsTracker tracker = tracker(20);
        tracker.recordXpGain(100, 0);
        tracker.recordKill(100, 0);
        tracker.setIdleTimeout(Duration.ofMinutes(1));

        tracker.update(59 * SECOND);
        assertTrue(tracker.snapshot().active());

        tracker.update(60 * SECOND);
        assertFalse(tracker.snapshot().active());
    }

    private CombatStatsTracker tracker(int windowSize) {
        return new CombatStatsTracker(windowSize, Duration.ofMinutes(5));
    }
}
