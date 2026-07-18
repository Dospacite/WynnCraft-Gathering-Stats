package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public record CombatSnapshot(
        boolean active,
        OptionalDouble xpPerHour,
        OptionalDouble itemsPerHour,
        OptionalLong xpUntilLevel,
        OptionalDouble secondsUntilLevel,
        OptionalDouble xpPerKill,
        OptionalDouble killsPerHour,
        boolean tradeMarketAvailable,
        OptionalDouble lePerHour,
        ProgressState progressState) {
    public static CombatSnapshot inactive(boolean tradeMarketAvailable) {
        return new CombatSnapshot(
                false,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalLong.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                tradeMarketAvailable,
                OptionalDouble.empty(),
                ProgressState.SYNCING);
    }
}
