package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalLong;

public final class ProfessionTargetCalculator {
    private ProfessionTargetCalculator() {}

    public static int nextTenthLevel(int currentLevel, int maximumLevel) {
        if (maximumLevel <= 0) {
            return 0;
        }
        if (currentLevel <= 0) {
            return Math.min(10, maximumLevel);
        }
        if (currentLevel >= maximumLevel) {
            return maximumLevel;
        }
        int nextTenth = ((currentLevel / 10) + 1) * 10;
        return Math.min(nextTenth, maximumLevel);
    }

    public static OptionalLong xpUntilTarget(
            int currentLevel,
            int targetLevel,
            long xpRemainingInCurrentLevel,
            int[] xpRequirements) {
        if (currentLevel <= 0
                || targetLevel <= currentLevel
                || xpRemainingInCurrentLevel < 0
                || xpRequirements == null) {
            return OptionalLong.empty();
        }

        long total = xpRemainingInCurrentLevel;
        for (int level = currentLevel + 1; level < targetLevel; level++) {
            int index = level - 1;
            if (index < 0 || index >= xpRequirements.length) {
                return OptionalLong.empty();
            }
            total = saturatingAdd(total, xpRequirements[index]);
        }
        return OptionalLong.of(total);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
