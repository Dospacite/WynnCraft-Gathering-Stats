package com.rousoftware.wynngatheringstats.stats;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public record TrackerSnapshot(
        Optional<GatheringProfession> profession,
        BombState bombState,
        OptionalDouble xpPerNode,
        OptionalDouble secondsPerNode,
        OptionalDouble oneStarItemsPerHour,
        OptionalDouble twoStarItemsPerHour,
        OptionalDouble threeStarItemsPerHour,
        Map<MaterialKey, Double> materialItemsPerHour,
        int xpSampleCount,
        int durationSampleCount,
        boolean activityWindowActive,
        boolean levelUpdatePending) {
    public boolean active() {
        return profession.isPresent() && activityWindowActive;
    }
}
