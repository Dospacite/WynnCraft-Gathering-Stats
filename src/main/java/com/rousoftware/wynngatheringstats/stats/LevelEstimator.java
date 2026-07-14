package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;
import java.util.OptionalLong;

public final class LevelEstimator {
    private LevelEstimator() {}

    public static LevelEstimate estimate(
            long remainingXp, OptionalDouble xpPerNode, OptionalDouble secondsPerNode) {
        if (remainingXp < 0 || xpPerNode.isEmpty() || !Double.isFinite(xpPerNode.getAsDouble())
                || xpPerNode.getAsDouble() <= 0) {
            return LevelEstimate.UNAVAILABLE;
        }

        double estimatedNodes = Math.ceil(remainingXp / xpPerNode.getAsDouble());
        if (!Double.isFinite(estimatedNodes) || estimatedNodes > Long.MAX_VALUE) {
            return LevelEstimate.UNAVAILABLE;
        }

        long nodes = (long) estimatedNodes;
        OptionalDouble seconds = OptionalDouble.empty();
        if (secondsPerNode.isPresent()
                && Double.isFinite(secondsPerNode.getAsDouble())
                && secondsPerNode.getAsDouble() >= 0) {
            double estimatedSeconds = nodes * secondsPerNode.getAsDouble();
            if (Double.isFinite(estimatedSeconds)) {
                seconds = OptionalDouble.of(estimatedSeconds);
            }
        }

        return new LevelEstimate(OptionalLong.of(nodes), seconds);
    }
}
