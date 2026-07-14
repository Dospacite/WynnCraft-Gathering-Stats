package com.rousoftware.wynngatheringstats.stats;

public record StatsDisplayConfig(
        boolean showProfessionHeader,
        boolean showBombStatus,
        boolean showXpPerNode,
        boolean showSecondsPerNode,
        boolean showOneStarItemsPerHour,
        boolean showTwoStarItemsPerHour,
        boolean showThreeStarItemsPerHour,
        boolean showLePerHour,
        boolean showXpUntilLevel,
        boolean showTimeUntilLevel,
        boolean showNodesUntilLevel) {}
