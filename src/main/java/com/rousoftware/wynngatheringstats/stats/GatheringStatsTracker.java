package com.rousoftware.wynngatheringstats.stats;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class GatheringStatsTracker {
    public static final int DEFAULT_WINDOW_SIZE = 20;
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);
    public static final Duration LEVEL_UPDATE_TIMEOUT = Duration.ofSeconds(5);
    public static final int MAX_PROFESSION_LEVEL = 132;

    private static final long NO_TIMESTAMP = Long.MIN_VALUE;

    private RollingAverage xpPerNode;
    private RollingAverage secondsPerNode;
    private int windowSize;
    private final Deque<Map<MaterialKey, Integer>> materialsByNode = new ArrayDeque<>();
    private long idleTimeoutNanos;
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

    public GatheringStatsTracker(int windowSize) {
        this(windowSize, DEFAULT_IDLE_TIMEOUT, LEVEL_UPDATE_TIMEOUT);
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
        this.windowSize = windowSize;
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

        materialsByNode.addLast(new HashMap<>());
        if (materialsByNode.size() > windowSize) {
            materialsByNode.removeFirst();
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

    public synchronized boolean recordMaterial(String name, int tier, int amount) {
        if (materialsByNode.isEmpty() || amount <= 0) {
            return false;
        }

        MaterialKey key = new MaterialKey(name, tier);
        materialsByNode.getLast().merge(key, amount, Integer::sum);
        return true;
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

    public synchronized void setWindowSize(int newWindowSize) {
        if (newWindowSize <= 0) {
            throw new IllegalArgumentException("window size must be positive");
        }
        if (newWindowSize == windowSize) {
            return;
        }

        windowSize = newWindowSize;
        xpPerNode = new RollingAverage(newWindowSize);
        secondsPerNode = new RollingAverage(newWindowSize);
        materialsByNode.clear();
        lastNodeNanos = NO_TIMESTAMP;
        lastActivityNanos = NO_TIMESTAMP;
        clearPendingLevelUpdate();
    }

    public synchronized void setIdleTimeout(Duration idleTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idle timeout must be positive");
        }
        idleTimeoutNanos = idleTimeout.toNanos();
    }

    public synchronized TrackerSnapshot snapshot() {
        MaterialRates materialRates = calculateMaterialRates();
        return new TrackerSnapshot(
                Optional.ofNullable(profession),
                bombState,
                xpPerNode.average(),
                secondsPerNode.average(),
                materialRates.tierRate(1),
                materialRates.tierRate(2),
                materialRates.tierRate(3),
                materialRates.byMaterial(),
                xpPerNode.size(),
                secondsPerNode.size(),
                lastActivityNanos != NO_TIMESTAMP,
                levelUpdatePending);
    }

    private void updateBombState(BombState currentBombState) {
        if (currentBombState.professionXpActive() != bombState.professionXpActive()) {
            xpPerNode.clear();
        }
        if (currentBombState.professionSpeedActive() != bombState.professionSpeedActive()) {
            secondsPerNode.clear();
            materialsByNode.clear();
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
        materialsByNode.clear();
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
        materialsByNode.clear();
        lastNodeNanos = NO_TIMESTAMP;
        lastActivityNanos = NO_TIMESTAMP;
        clearPendingLevelUpdate();
    }

    private void clearPendingLevelUpdate() {
        levelUpdatePending = false;
        levelBeforeUpdate = 0;
        levelUpdateStartedNanos = NO_TIMESTAMP;
    }

    private MaterialRates calculateMaterialRates() {
        OptionalDouble averageSeconds = secondsPerNode.average();
        if (materialsByNode.isEmpty()
                || averageSeconds.isEmpty()
                || averageSeconds.getAsDouble() <= 0
                || materialsByNode.stream().allMatch(Map::isEmpty)) {
            return MaterialRates.UNAVAILABLE;
        }

        double nodesPerHour = 3_600d / averageSeconds.getAsDouble();
        double scale = nodesPerHour / materialsByNode.size();
        double[] byTier = new double[3];
        Map<MaterialKey, Double> byMaterial = new HashMap<>();

        for (Map<MaterialKey, Integer> node : materialsByNode) {
            for (Map.Entry<MaterialKey, Integer> entry : node.entrySet()) {
                double itemsPerHour = entry.getValue() * scale;
                byTier[entry.getKey().tier() - 1] += itemsPerHour;
                byMaterial.merge(entry.getKey(), itemsPerHour, Double::sum);
            }
        }

        return new MaterialRates(byTier, Collections.unmodifiableMap(byMaterial), true);
    }

    private record MaterialRates(double[] byTier, Map<MaterialKey, Double> byMaterial, boolean available) {
        private static final MaterialRates UNAVAILABLE = new MaterialRates(new double[3], Map.of(), false);

        private OptionalDouble tierRate(int tier) {
            return available ? OptionalDouble.of(byTier[tier - 1]) : OptionalDouble.empty();
        }
    }
}
