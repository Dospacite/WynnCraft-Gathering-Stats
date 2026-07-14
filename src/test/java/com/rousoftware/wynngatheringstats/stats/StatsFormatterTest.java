package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class StatsFormatterTest {
    @Test
    void formatsNumbersAndUnavailableValues() {
        assertEquals("1,234.5", StatsFormatter.decimal(OptionalDouble.of(1234.45)));
        assertEquals("45,678", StatsFormatter.integer(OptionalLong.of(45678)));
        assertEquals(StatsFormatter.UNAVAILABLE, StatsFormatter.decimal(OptionalDouble.empty()));
        assertEquals(StatsFormatter.UNAVAILABLE, StatsFormatter.integer(OptionalLong.empty()));
    }

    @Test
    void formatsDurationsFromHoursThroughDays() {
        assertEquals("00:04:42", StatsFormatter.duration(OptionalDouble.of(282)));
        assertEquals("02:03:04", StatsFormatter.duration(OptionalDouble.of(7384)));
        assertEquals("2d 01:02:03", StatsFormatter.duration(OptionalDouble.of(176523)));
    }

    @Test
    void formatsProgressStates() {
        assertEquals("MAX", StatsFormatter.levelMetric(ProgressState.MAX_LEVEL, OptionalLong.empty()));
        assertEquals("Updating...", StatsFormatter.levelMetric(ProgressState.UPDATING, OptionalLong.empty()));
        assertEquals(StatsFormatter.UNAVAILABLE, StatsFormatter.levelMetric(ProgressState.SYNCING, OptionalLong.empty()));
        assertEquals("38", StatsFormatter.levelMetric(ProgressState.READY, OptionalLong.of(38)));
    }
}
