package com.rousoftware.wynngatheringstats.stats;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;

public final class StatsFormatter {
    public static final String UNAVAILABLE = "—";

    private StatsFormatter() {}

    public static String decimal(OptionalDouble value) {
        if (value.isEmpty() || !Double.isFinite(value.getAsDouble())) {
            return UNAVAILABLE;
        }
        return decimalFormat().format(value.getAsDouble());
    }

    public static String integer(OptionalLong value) {
        return value.isPresent() ? String.format(Locale.US, "%,d", value.getAsLong()) : UNAVAILABLE;
    }

    public static String duration(OptionalDouble seconds) {
        if (seconds.isEmpty() || !Double.isFinite(seconds.getAsDouble()) || seconds.getAsDouble() < 0) {
            return UNAVAILABLE;
        }

        long totalSeconds = Math.round(seconds.getAsDouble());
        long days = totalSeconds / 86_400;
        long remaining = totalSeconds % 86_400;
        long hours = remaining / 3_600;
        long minutes = (remaining % 3_600) / 60;
        long secs = remaining % 60;

        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, secs);
    }

    public static String levelMetric(ProgressState state, OptionalLong value) {
        return switch (state) {
            case MAX_LEVEL -> "MAX";
            case UPDATING -> "Updating...";
            case SYNCING -> UNAVAILABLE;
            case READY -> integer(value);
        };
    }

    public static String levelDuration(ProgressState state, OptionalDouble value) {
        return switch (state) {
            case MAX_LEVEL -> "MAX";
            case UPDATING -> "Updating...";
            case SYNCING -> UNAVAILABLE;
            case READY -> duration(value);
        };
    }

    private static DecimalFormat decimalFormat() {
        DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
        format.setGroupingUsed(true);
        return format;
    }
}
