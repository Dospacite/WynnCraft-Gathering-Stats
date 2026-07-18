package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;

public record CombatTrackerSnapshot(
        boolean active,
        OptionalDouble xpPerHour,
        OptionalDouble itemsPerHour,
        OptionalDouble xpPerKill,
        OptionalDouble killsPerHour,
        int xpSampleCount,
        int killSampleCount) {}
