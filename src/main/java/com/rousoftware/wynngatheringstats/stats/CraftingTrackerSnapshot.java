package com.rousoftware.wynngatheringstats.stats;

import java.util.Optional;
import java.util.OptionalDouble;

public record CraftingTrackerSnapshot(
        Optional<CraftingProfession> profession,
        BombState bombState,
        OptionalDouble xpPerCraft,
        Optional<CraftingRecipe> recipe,
        int xpSampleCount,
        boolean activityWindowActive,
        boolean levelUpdatePending) {
    public boolean active() {
        return profession.isPresent() && recipe.isPresent() && activityWindowActive;
    }
}
