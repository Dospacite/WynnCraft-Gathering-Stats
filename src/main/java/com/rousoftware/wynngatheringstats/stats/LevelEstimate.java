package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public record LevelEstimate(OptionalLong nodes, OptionalDouble seconds) {
    public static final LevelEstimate UNAVAILABLE =
            new LevelEstimate(OptionalLong.empty(), OptionalDouble.empty());
}
