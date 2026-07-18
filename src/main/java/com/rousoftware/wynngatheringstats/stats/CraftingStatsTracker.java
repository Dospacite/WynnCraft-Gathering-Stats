package com.rousoftware.wynngatheringstats.stats;

import java.time.Duration;
import java.util.Optional;

public final class CraftingStatsTracker {
    public static final int DEFAULT_WINDOW_SIZE = 20;
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration LEVEL_UPDATE_TIMEOUT = Duration.ofSeconds(5);
    public static final int MAX_PROFESSION_LEVEL = 132;

    private static final long NO_TIMESTAMP = Long.MIN_VALUE;

    private RollingAverage xpPerCraft;
    private int windowSize;
    private long idleTimeoutNanos;
    private final long levelUpdateTimeoutNanos;

    private CraftingProfession profession;
    private CraftingRecipe recipe;
    private BombState bombState = BombState.NONE;
    private BombState lastCraftBombState;
    private long lastActivityNanos = NO_TIMESTAMP;
    private boolean levelUpdatePending;
    private int levelBeforeUpdate;
    private long levelUpdateStartedNanos = NO_TIMESTAMP;

    public CraftingStatsTracker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_IDLE_TIMEOUT, LEVEL_UPDATE_TIMEOUT);
    }

    public CraftingStatsTracker(int windowSize) {
        this(windowSize, DEFAULT_IDLE_TIMEOUT, LEVEL_UPDATE_TIMEOUT);
    }

    CraftingStatsTracker(int windowSize, Duration idleTimeout) {
        this(windowSize, idleTimeout, LEVEL_UPDATE_TIMEOUT);
    }

    CraftingStatsTracker(int windowSize, Duration idleTimeout, Duration levelUpdateTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idle timeout must be positive");
        }
        if (levelUpdateTimeout.isNegative() || levelUpdateTimeout.isZero()) {
            throw new IllegalArgumentException("level update timeout must be positive");
        }
        xpPerCraft = new RollingAverage(windowSize);
        this.windowSize = windowSize;
        idleTimeoutNanos = idleTimeout.toNanos();
        levelUpdateTimeoutNanos = levelUpdateTimeout.toNanos();
    }

    public synchronized void recordCraft(
            CraftingProfession newProfession,
            double gainedXp,
            CraftingRecipe newRecipe,
            BombState currentBombState,
            long nowNanos) {
        recordCraft(newProfession, gainedXp, 0, 0, newRecipe, currentBombState, nowNanos);
    }

    public synchronized void recordCraft(
            CraftingProfession newProfession,
            double gainedXp,
            double currentXpPercentage,
            int currentLevel,
            CraftingRecipe newRecipe,
            BombState currentBombState,
            long nowNanos) {
        if (newProfession == null || newRecipe == null || currentBombState == null) {
            throw new IllegalArgumentException("profession, recipe, and bomb state are required");
        }

        resetForIdleIfNeeded(nowNanos);
        boolean recipeChanged = profession != newProfession || !newRecipe.hasSameInputsAs(recipe);
        if (recipeChanged) {
            xpPerCraft.clear();
            profession = newProfession;
            recipe = newRecipe;
            lastCraftBombState = null;
            clearPendingLevelUpdate();
        } else {
            // A speed-bomb sample only reveals floor(normal / 2). Preserve a previously observed
            // unbombed material count so odd requirements remain exact across bomb transitions.
            if (!currentBombState.professionSpeedActive()) {
                recipe = newRecipe;
            }
            if (lastCraftBombState != null
                    && lastCraftBombState.professionXpActive() != currentBombState.professionXpActive()) {
                // A bomb being thrown or expiring does not disturb the current display. The XP
                // window changes only after a craft is actually completed under the other XP multiplier.
                xpPerCraft.clear();
            }
        }

        if (Double.isFinite(gainedXp) && gainedXp > 0) {
            xpPerCraft.add(gainedXp);
        }
        bombState = currentBombState;
        lastCraftBombState = currentBombState;
        lastActivityNanos = nowNanos;
        observeLevel(currentLevel, nowNanos);
        if (currentXpPercentage >= 100d && currentLevel > 0 && currentLevel < MAX_PROFESSION_LEVEL) {
            levelUpdatePending = true;
            levelBeforeUpdate = currentLevel;
            levelUpdateStartedNanos = nowNanos;
        }
    }

    public synchronized void updateEnvironment(BombState currentBombState, long nowNanos) {
        updateEnvironment(currentBombState, 0, nowNanos);
    }

    public synchronized void updateEnvironment(BombState currentBombState, int currentLevel, long nowNanos) {
        if (currentBombState == null) {
            throw new IllegalArgumentException("bomb state is required");
        }
        bombState = currentBombState;
        resetForIdleIfNeeded(nowNanos);
        observeLevel(currentLevel, nowNanos);
    }

    public synchronized void reset() {
        clearSession();
    }

    public synchronized void setWindowSize(int newWindowSize) {
        if (newWindowSize <= 0) {
            throw new IllegalArgumentException("window size must be positive");
        }
        if (newWindowSize == windowSize) {
            return;
        }
        windowSize = newWindowSize;
        xpPerCraft = new RollingAverage(newWindowSize);
        lastActivityNanos = NO_TIMESTAMP;
        lastCraftBombState = null;
        clearPendingLevelUpdate();
    }

    public synchronized void setIdleTimeout(Duration idleTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idle timeout must be positive");
        }
        idleTimeoutNanos = idleTimeout.toNanos();
    }

    public synchronized CraftingTrackerSnapshot snapshot() {
        return new CraftingTrackerSnapshot(
                Optional.ofNullable(profession),
                bombState,
                xpPerCraft.average(),
                Optional.ofNullable(recipe),
                xpPerCraft.size(),
                lastActivityNanos != NO_TIMESTAMP,
                levelUpdatePending);
    }

    private void resetForIdleIfNeeded(long nowNanos) {
        if (lastActivityNanos == NO_TIMESTAMP || nowNanos < lastActivityNanos) {
            return;
        }
        if (nowNanos - lastActivityNanos < idleTimeoutNanos) {
            return;
        }
        xpPerCraft.clear();
        lastActivityNanos = NO_TIMESTAMP;
        lastCraftBombState = null;
        clearPendingLevelUpdate();
    }

    private void observeLevel(int currentLevel, long nowNanos) {
        if (!levelUpdatePending || currentLevel <= 0) {
            return;
        }
        boolean modelUpdated = currentLevel > levelBeforeUpdate;
        boolean timedOut = nowNanos >= levelUpdateStartedNanos
                && nowNanos - levelUpdateStartedNanos >= levelUpdateTimeoutNanos;
        if (modelUpdated || timedOut || currentLevel >= MAX_PROFESSION_LEVEL) {
            clearPendingLevelUpdate();
        }
    }

    private void clearPendingLevelUpdate() {
        levelUpdatePending = false;
        levelBeforeUpdate = 0;
        levelUpdateStartedNanos = NO_TIMESTAMP;
    }

    private void clearSession() {
        profession = null;
        recipe = null;
        xpPerCraft.clear();
        lastActivityNanos = NO_TIMESTAMP;
        lastCraftBombState = null;
        clearPendingLevelUpdate();
    }
}
