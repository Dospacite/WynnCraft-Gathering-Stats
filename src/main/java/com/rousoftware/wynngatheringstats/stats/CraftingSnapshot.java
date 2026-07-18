package com.rousoftware.wynngatheringstats.stats;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public record CraftingSnapshot(
        boolean active,
        CraftingProfession profession,
        int level,
        int targetLevel,
        BombState bombState,
        OptionalDouble xpPerCraft,
        ProgressState progressState,
        OptionalLong xpUntilTarget,
        OptionalLong craftsUntilTarget,
        boolean tradeMarketAvailable,
        List<CraftingComponentProjection> components) {
    public CraftingSnapshot {
        components = List.copyOf(components);
    }

    public static CraftingSnapshot inactive(BombState bombState, boolean tradeMarketAvailable) {
        return new CraftingSnapshot(
                false,
                null,
                0,
                0,
                bombState,
                OptionalDouble.empty(),
                ProgressState.SYNCING,
                OptionalLong.empty(),
                OptionalLong.empty(),
                tradeMarketAvailable,
                List.of());
    }
}
