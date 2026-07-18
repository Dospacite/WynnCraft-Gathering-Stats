package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalDouble;

public record CraftingComponentProjection(
        CraftingComponent component, long requiredAmount, OptionalDouble emeraldCost) {}
