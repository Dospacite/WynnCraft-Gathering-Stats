package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatheringStatsTrackerTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void firstNodeHasXpButNeedsASecondNodeForTiming() {
        GatheringStatsTracker tracker = tracker();

        tracker.recordNode(GatheringProfession.MINING, 100, 12.5, 50, BombState.NONE, 0);
        TrackerSnapshot first = tracker.snapshot();

        assertEquals(100, first.xpPerNode().orElseThrow(), 0.0001);
        assertTrue(first.secondsPerNode().isEmpty());

        tracker.recordNode(GatheringProfession.MINING, 120, 13, 50, BombState.NONE, 8 * SECOND);
        TrackerSnapshot second = tracker.snapshot();

        assertEquals(110, second.xpPerNode().orElseThrow(), 0.0001);
        assertEquals(8, second.secondsPerNode().orElseThrow(), 0.0001);
    }

    @Test
    void rollingWindowKeepsOnlyTheLatestTwentySamples() {
        GatheringStatsTracker tracker = tracker();

        for (int i = 1; i <= 25; i++) {
            tracker.recordNode(GatheringProfession.FARMING, i, i, 20, BombState.NONE, i * SECOND);
        }

        TrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(20, snapshot.xpSampleCount());
        assertEquals(15.5, snapshot.xpPerNode().orElseThrow(), 0.0001);
        assertEquals(20, snapshot.durationSampleCount());
        assertEquals(1, snapshot.secondsPerNode().orElseThrow(), 0.0001);
    }

    @Test
    void configurableWindowAppliesToXpAndResetsExistingSamples() {
        GatheringStatsTracker tracker = new GatheringStatsTracker(3, Duration.ofMinutes(5), Duration.ofSeconds(5));
        for (int i = 1; i <= 5; i++) {
            tracker.recordNode(GatheringProfession.MINING, i, i, 20, BombState.NONE, i * SECOND);
        }

        assertEquals(3, tracker.snapshot().xpSampleCount());
        assertEquals(4, tracker.snapshot().xpPerNode().orElseThrow(), 0.0001);

        tracker.setWindowSize(2);
        TrackerSnapshot resized = tracker.snapshot();
        assertEquals(GatheringProfession.MINING, resized.profession().orElseThrow());
        assertTrue(resized.xpPerNode().isEmpty());
        assertTrue(resized.secondsPerNode().isEmpty());

        tracker.recordNode(GatheringProfession.MINING, 10, 6, 20, BombState.NONE, 6 * SECOND);
        tracker.recordNode(GatheringProfession.MINING, 20, 7, 20, BombState.NONE, 7 * SECOND);
        tracker.recordNode(GatheringProfession.MINING, 30, 8, 20, BombState.NONE, 8 * SECOND);
        assertEquals(2, tracker.snapshot().xpSampleCount());
        assertEquals(25, tracker.snapshot().xpPerNode().orElseThrow(), 0.0001);
    }

    @Test
    void bombChangesResetOnlyTheAffectedMetric() {
        GatheringStatsTracker tracker = tracker();

        tracker.recordNode(GatheringProfession.FISHING, 10, 10, 40, BombState.NONE, 0);
        tracker.recordNode(GatheringProfession.FISHING, 20, 11, 40, BombState.NONE, 10 * SECOND);

        BombState xpBomb = new BombState(true, false);
        tracker.updateEnvironment(xpBomb, 40, 11 * SECOND);
        TrackerSnapshot afterXpBomb = tracker.snapshot();
        assertTrue(afterXpBomb.xpPerNode().isEmpty());
        assertEquals(10, afterXpBomb.secondsPerNode().orElseThrow(), 0.0001);

        tracker.recordNode(GatheringProfession.FISHING, 40, 12, 40, xpBomb, 20 * SECOND);
        assertEquals(40, tracker.snapshot().xpPerNode().orElseThrow(), 0.0001);
        assertEquals(10, tracker.snapshot().secondsPerNode().orElseThrow(), 0.0001);

        tracker.updateEnvironment(BombState.NONE, 40, 20 * SECOND + 1);
        assertTrue(tracker.snapshot().xpPerNode().isEmpty());
        tracker.recordNode(GatheringProfession.FISHING, 20, 12.5, 40, BombState.NONE, 21 * SECOND);
        assertEquals(20, tracker.snapshot().xpPerNode().orElseThrow(), 0.0001);

        BombState bothBombs = new BombState(false, true);
        tracker.updateEnvironment(bothBombs, 40, 21 * SECOND);
        TrackerSnapshot afterSpeedBomb = tracker.snapshot();
        assertEquals(20, afterSpeedBomb.xpPerNode().orElseThrow(), 0.0001);
        assertTrue(afterSpeedBomb.secondsPerNode().isEmpty());

        tracker.recordNode(GatheringProfession.FISHING, 50, 13, 40, bothBombs, 30 * SECOND);
        assertTrue(tracker.snapshot().secondsPerNode().isEmpty());
        tracker.recordNode(GatheringProfession.FISHING, 50, 14, 40, bothBombs, 34 * SECOND);
        assertEquals(4, tracker.snapshot().secondsPerNode().orElseThrow(), 0.0001);
    }

    @Test
    void fiveMinuteIdleGapClearsSamplesButKeepsProfessionContext() {
        GatheringStatsTracker tracker = tracker();

        tracker.recordNode(GatheringProfession.WOODCUTTING, 10, 1, 10, BombState.NONE, 0);
        tracker.recordNode(GatheringProfession.WOODCUTTING, 10, 2, 10, BombState.NONE, 10 * SECOND);
        tracker.updateEnvironment(BombState.NONE, 10, 310 * SECOND);

        TrackerSnapshot idle = tracker.snapshot();
        assertEquals(GatheringProfession.WOODCUTTING, idle.profession().orElseThrow());
        assertTrue(idle.xpPerNode().isEmpty());
        assertTrue(idle.secondsPerNode().isEmpty());

        tracker.recordNode(GatheringProfession.WOODCUTTING, 30, 3, 10, BombState.NONE, 311 * SECOND);
        assertEquals(30, tracker.snapshot().xpPerNode().orElseThrow(), 0.0001);
        assertTrue(tracker.snapshot().secondsPerNode().isEmpty());
    }

    @Test
    void professionChangeAndManualResetClearTheSession() {
        GatheringStatsTracker tracker = tracker();
        tracker.recordNode(GatheringProfession.MINING, 10, 1, 10, BombState.NONE, 0);
        tracker.recordNode(GatheringProfession.FARMING, 50, 1, 10, BombState.NONE, SECOND);

        TrackerSnapshot changed = tracker.snapshot();
        assertEquals(GatheringProfession.FARMING, changed.profession().orElseThrow());
        assertEquals(50, changed.xpPerNode().orElseThrow(), 0.0001);
        assertTrue(changed.secondsPerNode().isEmpty());

        tracker.reset();
        assertFalse(tracker.snapshot().active());
    }

    @Test
    void levelUpdateClearsWhenTheModelAdvancesOrTimesOut() {
        GatheringStatsTracker tracker = tracker();
        tracker.recordNode(GatheringProfession.MINING, 10, 100, 5, BombState.NONE, 0);
        assertTrue(tracker.snapshot().levelUpdatePending());

        tracker.updateEnvironment(BombState.NONE, 6, SECOND);
        assertFalse(tracker.snapshot().levelUpdatePending());

        tracker.recordNode(GatheringProfession.MINING, 10, 100, 6, BombState.NONE, 2 * SECOND);
        tracker.updateEnvironment(BombState.NONE, 6, 7 * SECOND);
        assertFalse(tracker.snapshot().levelUpdatePending());
    }

    @Test
    void calculatesPerTierAndPerMaterialHourlyRates() {
        GatheringStatsTracker tracker = tracker();
        tracker.recordNode(GatheringProfession.MINING, 10, 1, 10, BombState.NONE, 0);
        tracker.recordMaterial("Copper Ingot", 1, 2);
        tracker.recordNode(GatheringProfession.MINING, 10, 2, 10, BombState.NONE, 10 * SECOND);
        tracker.recordMaterial("Copper Ingot", 2, 1);

        TrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(360, snapshot.oneStarItemsPerHour().orElseThrow(), 0.0001);
        assertEquals(180, snapshot.twoStarItemsPerHour().orElseThrow(), 0.0001);
        assertEquals(0, snapshot.threeStarItemsPerHour().orElseThrow(), 0.0001);
        assertEquals(360, snapshot.materialItemsPerHour().get(new MaterialKey("Copper Ingot", 1)), 0.0001);
        assertEquals(180, snapshot.materialItemsPerHour().get(new MaterialKey("Copper Ingot", 2)), 0.0001);
    }

    @Test
    void itemRatesWaitForTimingAndResetWithSpeedChanges() {
        GatheringStatsTracker tracker = tracker();
        tracker.recordNode(GatheringProfession.FARMING, 10, 1, 10, BombState.NONE, 0);
        tracker.recordMaterial("Wheat Grains", 1, 1);
        assertTrue(tracker.snapshot().oneStarItemsPerHour().isEmpty());

        tracker.recordNode(GatheringProfession.FARMING, 10, 2, 10, BombState.NONE, 10 * SECOND);
        tracker.recordMaterial("Wheat Grains", 1, 1);
        assertTrue(tracker.snapshot().oneStarItemsPerHour().isPresent());

        tracker.updateEnvironment(new BombState(false, true), 10, 11 * SECOND);
        assertTrue(tracker.snapshot().oneStarItemsPerHour().isEmpty());
        assertTrue(tracker.snapshot().materialItemsPerHour().isEmpty());
    }

    private GatheringStatsTracker tracker() {
        return new GatheringStatsTracker(20, Duration.ofMinutes(5), Duration.ofSeconds(5));
    }
}
