package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public record GatheringSnapshot(
        boolean active,
        GatheringProfession profession,
        int level,
        BombState bombState,
        OptionalDouble xpPerNode,
        OptionalDouble secondsPerNode,
        ProgressState progressState,
        OptionalLong xpUntilLevel,
        OptionalLong nodesUntilLevel,
        OptionalDouble secondsUntilLevel) {
    public static GatheringSnapshot inactive(BombState bombState) {
        return new GatheringSnapshot(
                false,
                null,
                0,
                bombState,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                ProgressState.SYNCING,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalDouble.empty());
    }
}
