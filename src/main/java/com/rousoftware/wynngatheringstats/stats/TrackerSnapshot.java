package com.rousoftware.wynngatheringstats.stats;

import java.util.Optional;
import java.util.OptionalDouble;

public record TrackerSnapshot(
        Optional<GatheringProfession> profession,
        BombState bombState,
        OptionalDouble xpPerNode,
        OptionalDouble secondsPerNode,
        int xpSampleCount,
        int durationSampleCount,
        boolean levelUpdatePending) {
    public boolean active() {
        return profession.isPresent();
    }
}
