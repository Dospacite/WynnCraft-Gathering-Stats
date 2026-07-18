package com.rousoftware.wynngatheringstats.stats;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalDouble;

public final class CombatStatsTracker {
    public static final int DEFAULT_WINDOW_SIZE = 20;
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);

    private static final long NO_TIMESTAMP = Long.MIN_VALUE;
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final int windowSize;
    private long idleTimeoutNanos;
    private final RollingAverage xpGains;
    private final RollingAverage secondsPerXpGain;
    private final RollingAverage xpPerKill;
    private final RollingAverage secondsPerKill;
    private final Deque<Integer> itemsByKill = new ArrayDeque<>();

    private boolean active;
    private long lastXpGainNanos = NO_TIMESTAMP;
    private long lastKillNanos = NO_TIMESTAMP;
    private long lastActivityNanos = NO_TIMESTAMP;
    private int pendingItems;

    public CombatStatsTracker() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_IDLE_TIMEOUT);
    }

    public CombatStatsTracker(int windowSize, Duration idleTimeout) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }

        this.windowSize = windowSize;
        this.idleTimeoutNanos = idleTimeout.toNanos();
        this.xpGains = new RollingAverage(windowSize);
        this.secondsPerXpGain = new RollingAverage(windowSize);
        this.xpPerKill = new RollingAverage(windowSize);
        this.secondsPerKill = new RollingAverage(windowSize);
    }

    public void setIdleTimeout(Duration idleTimeout) {
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        idleTimeoutNanos = idleTimeout.toNanos();
    }

    public void recordXpGain(double gainedXp, long nowNanos) {
        if (!Double.isFinite(gainedXp) || gainedXp <= 0) {
            return;
        }

        clearIfIdle(nowNanos);
        if (lastXpGainNanos != NO_TIMESTAMP && nowNanos > lastXpGainNanos) {
            secondsPerXpGain.add((nowNanos - lastXpGainNanos) / NANOS_PER_SECOND);
        }
        xpGains.add(gainedXp);
        lastXpGainNanos = nowNanos;
        markActive(nowNanos);
    }

    public void recordKill(double gainedXp, long nowNanos) {
        if (!Double.isFinite(gainedXp) || gainedXp < 0) {
            return;
        }

        clearIfIdle(nowNanos);
        if (lastKillNanos != NO_TIMESTAMP && nowNanos > lastKillNanos) {
            secondsPerKill.add((nowNanos - lastKillNanos) / NANOS_PER_SECOND);
        }
        xpPerKill.add(gainedXp);
        itemsByKill.addLast(pendingItems);
        pendingItems = 0;
        while (itemsByKill.size() > windowSize) {
            itemsByKill.removeFirst();
        }

        lastKillNanos = nowNanos;
        markActive(nowNanos);
    }

    public void recordItemPickup(int amount, long nowNanos) {
        if (amount <= 0) {
            return;
        }

        clearIfIdle(nowNanos);
        if (itemsByKill.isEmpty()) {
            pendingItems = saturatingAdd(pendingItems, amount);
        } else {
            int updated = saturatingAdd(itemsByKill.removeLast(), amount);
            itemsByKill.addLast(updated);
        }
        lastActivityNanos = nowNanos;
    }

    public void update(long nowNanos) {
        clearIfIdle(nowNanos);
    }

    public CombatTrackerSnapshot snapshot() {
        OptionalDouble killsPerHour = hourlyRate(OptionalDouble.of(1d), secondsPerKill.average());
        OptionalDouble xpPerHour = hourlyRate(xpGains.average(), secondsPerXpGain.average());
        if (xpPerHour.isEmpty()) {
            xpPerHour = product(xpPerKill.average(), killsPerHour);
        }
        OptionalDouble itemsPerHour = OptionalDouble.empty();

        if (killsPerHour.isPresent() && !itemsByKill.isEmpty()) {
            long itemSum = 0;
            for (int amount : itemsByKill) {
                itemSum += amount;
            }
            double itemsPerKill = itemSum / (double) itemsByKill.size();
            itemsPerHour = OptionalDouble.of(itemsPerKill * killsPerHour.getAsDouble());
        }

        return new CombatTrackerSnapshot(
                active,
                xpPerHour,
                itemsPerHour,
                xpPerKill.average(),
                killsPerHour,
                xpGains.size(),
                xpPerKill.size());
    }

    public void reset() {
        active = false;
        xpGains.clear();
        secondsPerXpGain.clear();
        xpPerKill.clear();
        secondsPerKill.clear();
        itemsByKill.clear();
        pendingItems = 0;
        lastXpGainNanos = NO_TIMESTAMP;
        lastKillNanos = NO_TIMESTAMP;
        lastActivityNanos = NO_TIMESTAMP;
    }

    private void markActive(long nowNanos) {
        active = true;
        lastActivityNanos = nowNanos;
    }

    private void clearIfIdle(long nowNanos) {
        if (lastActivityNanos != NO_TIMESTAMP
                && nowNanos > lastActivityNanos
                && nowNanos - lastActivityNanos >= idleTimeoutNanos) {
            reset();
        }
    }

    private static OptionalDouble hourlyRate(OptionalDouble amount, OptionalDouble seconds) {
        if (amount.isEmpty() || seconds.isEmpty() || seconds.getAsDouble() <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(amount.getAsDouble() * 3_600d / seconds.getAsDouble());
    }

    private static OptionalDouble product(OptionalDouble left, OptionalDouble right) {
        if (left.isEmpty() || right.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(left.getAsDouble() * right.getAsDouble());
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
