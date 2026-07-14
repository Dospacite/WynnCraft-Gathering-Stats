package com.rousoftware.wynngatheringstats.stats;

import java.time.Duration;
import java.util.Optional;

public final class GatheringStatsTracker {
    public static final int DEFAULT_WINDOW_SIZE = 20;
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration LEVEL_UPDATE_TIMEOUT = Duration.ofSeconds(5);
    public static final int MAX_PROFESSION_LEVEL = 132;

    private static final long NO_TIMESTAMP = Long.MIN_VALUE;

    private final RollingAverage xpPerNode;
    private final RollingAverage secondsPerNode;
    private final long idleTimeoutNanos;
    private final long levelUpdateTimeoutNanos;

    private GatheringProfession profession;
    private BombState bombState = BombState.NONE;
    private long lastNodeNanos = NO_TIMESTAMP;
    private long lastActivityNanos = NO_TIMESTAMP;
    private boolean levelUpdatePending;
    private int levelBeforeUpdate;
    private long levelUpdateStartedNanos = NO_TIMESTAMP;

    public GatheringStatsTracker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_IDLE_TIMEOUT, LEVEL_UPDATE_TIMEOUT);
    }

    GatheringStatsTracker(int windowSize, Duration idleTimeout, Duration levelUpdateTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idle timeout must be positive");
        }
        if (levelUpdateTimeout.isNegative() || levelUpdateTimeout.isZero()) {
            throw new IllegalArgumentException("level update timeout must be positive");
        }

        xpPerNode = new RollingAverage(windowSize);
        secondsPerNode = new RollingAverage(windowSize);
        idleTimeoutNanos = idleTimeout.toNanos();
        levelUpdateTimeoutNanos = levelUpdateTimeout.toNanos();
    }

    public synchronized void recordNode(
            GatheringProfession newProfession,
            double gainedXp,
            double currentXpPercentage,
            int currentLevel,
            BombState currentBombState,
            long nowNanos) {
        if (newProfession == null || currentBombState == null) {
            throw new IllegalArgumentException("profession and bomb state are required");
        }

        if (profession != newProfession) {
            clearSession();
            profession = newProfession;
            bombState = currentBombState;
        } else {
            updateBombState(currentBombState);
            resetForIdleIfNeeded(nowNanos);
        }

        if (Double.isFinite(gainedXp) && gainedXp > 0) {
            xpPerNode.add(gainedXp);
        }

        if (lastNodeNanos != NO_TIMESTAMP && nowNanos >= lastNodeNanos) {
            secondsPerNode.add((nowNanos - lastNodeNanos) / 1_000_000_000d);
        }

        lastNodeNanos = nowNanos;
        lastActivityNanos = nowNanos;

        observeLevel(currentLevel, nowNanos);
        if (currentXpPercentage >= 100d && currentLevel > 0 && currentLevel < MAX_PROFESSION_LEVEL) {
            levelUpdatePending = true;
            levelBeforeUpdate = currentLevel;
            levelUpdateStartedNanos = nowNanos;
        }
    }

    public synchronized void updateEnvironment(BombState currentBombState, int currentLevel, long nowNanos) {
        if (currentBombState == null) {
            throw new IllegalArgumentException("bomb state is required");
        }
        updateBombState(currentBombState);
        resetForIdleIfNeeded(nowNanos);
        observeLevel(currentLevel, nowNanos);
    }

    public synchronized void reset() {
        clearSession();
    }

    public synchronized TrackerSnapshot snapshot() {
        return new TrackerSnapshot(
                Optional.ofNullable(profession),
                bombState,
                xpPerNode.average(),
                secondsPerNode.average(),
                xpPerNode.size(),
                secondsPerNode.size(),
                levelUpdatePending);
    }

    private void updateBombState(BombState currentBombState) {
        if (currentBombState.professionXpActive() != bombState.professionXpActive()) {
            xpPerNode.clear();
        }
        if (currentBombState.professionSpeedActive() != bombState.professionSpeedActive()) {
            secondsPerNode.clear();
            lastNodeNanos = NO_TIMESTAMP;
        }
        bombState = currentBombState;
    }

    private void resetForIdleIfNeeded(long nowNanos) {
        if (lastActivityNanos == NO_TIMESTAMP || nowNanos < lastActivityNanos) {
            return;
        }
        if (nowNanos - lastActivityNanos < idleTimeoutNanos) {
            return;
        }

        xpPerNode.clear();
        secondsPerNode.clear();
        lastNodeNanos = NO_TIMESTAMP;
        lastActivityNanos = NO_TIMESTAMP;
        clearPendingLevelUpdate();
    }

    private void observeLevel(int currentLevel, long nowNanos) {
        if (!levelUpdatePending) {
            return;
        }
        boolean modelUpdated = currentLevel > levelBeforeUpdate;
        boolean timedOut = nowNanos >= levelUpdateStartedNanos
                && nowNanos - levelUpdateStartedNanos >= levelUpdateTimeoutNanos;
        if (modelUpdated || timedOut || currentLevel >= MAX_PROFESSION_LEVEL) {
            clearPendingLevelUpdate();
        }
    }

    private void clearSession() {
        profession = null;
        xpPerNode.clear();
        secondsPerNode.clear();
        lastNodeNanos = NO_TIMESTAMP;
        lastActivityNanos = NO_TIMESTAMP;
        clearPendingLevelUpdate();
    }

    private void clearPendingLevelUpdate() {
        levelUpdatePending = false;
        levelBeforeUpdate = 0;
        levelUpdateStartedNanos = NO_TIMESTAMP;
    }
}
