package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CraftingStatsTrackerTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void calculatesXpPerCraftOverTheConfiguredRollingWindow() {
        CraftingStatsTracker tracker = new CraftingStatsTracker(3, Duration.ofMinutes(5));
        CraftingRecipe recipe = recipe("Copper Ingot", "Oak Plank", "Rotten Flesh");

        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 100, recipe, BombState.NONE, 0);
        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 200, recipe, BombState.NONE, SECOND);
        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 300, recipe, BombState.NONE, 2 * SECOND);
        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 500, recipe, BombState.NONE, 3 * SECOND);

        CraftingTrackerSnapshot snapshot = tracker.snapshot();
        assertEquals(3, snapshot.xpSampleCount());
        assertEquals(1_000d / 3d, snapshot.xpPerCraft().orElseThrow(), 0.0001);
    }

    @Test
    void recipeChangesResetXpSamples() {
        CraftingStatsTracker tracker = tracker();
        CraftingRecipe copper = recipe("Copper Ingot", "Oak Plank", "Rotten Flesh");
        CraftingRecipe gold = recipe("Gold Ingot", "Willow Plank", "Rotten Flesh");
        CraftingRecipe newIngredient = recipe("Gold Ingot", "Willow Plank", "Forest Web");

        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 100, copper, BombState.NONE, 0);
        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 120, copper, BombState.NONE, SECOND);
        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 400, gold, BombState.NONE, 2 * SECOND);
        assertEquals(1, tracker.snapshot().xpSampleCount());
        assertEquals(400, tracker.snapshot().xpPerCraft().orElseThrow(), 0.0001);

        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 600, newIngredient, BombState.NONE, 3 * SECOND);
        assertEquals(1, tracker.snapshot().xpSampleCount());
        assertEquals(600, tracker.snapshot().xpPerCraft().orElseThrow(), 0.0001);
    }

    @Test
    void speedBombQuantityChangesDoNotLookLikeNewBaseItems() {
        CraftingStatsTracker tracker = tracker();
        CraftingRecipe normal = new CraftingRecipe(
                List.of(material("Cobalt Ingot", 2, 7), material("Dark Plank", 2, 5)),
                List.of(ingredient("Strong Flesh", 1)));
        CraftingRecipe inferredFromBomb = new CraftingRecipe(
                List.of(material("Cobalt Ingot", 2, 6), material("Dark Plank", 2, 4)),
                List.of(ingredient("Strong Flesh", 1)));

        tracker.recordCraft(CraftingProfession.WEAPONSMITHING, 100, normal, BombState.NONE, 0);
        tracker.recordCraft(
                CraftingProfession.WEAPONSMITHING,
                110,
                inferredFromBomb,
                new BombState(false, true),
                SECOND);

        assertEquals(2, tracker.snapshot().xpSampleCount());
        assertEquals(normal.materials(), tracker.snapshot().recipe().orElseThrow().materials());
    }

    @Test
    void xpBombChangesResetOnlyWhenACraftUsesTheNewState() {
        CraftingStatsTracker tracker = tracker();
        CraftingRecipe recipe = recipe("Copper Ingot", "Oak Plank", "Rotten Flesh");

        tracker.recordCraft(CraftingProfession.WOODWORKING, 100, recipe, BombState.NONE, 0);
        BombState xpBomb = new BombState(true, false);
        tracker.updateEnvironment(xpBomb, SECOND);
        assertEquals(100, tracker.snapshot().xpPerCraft().orElseThrow(), 0.0001);

        tracker.recordCraft(CraftingProfession.WOODWORKING, 200, recipe, xpBomb, 2 * SECOND);
        assertEquals(1, tracker.snapshot().xpSampleCount());
        assertEquals(200, tracker.snapshot().xpPerCraft().orElseThrow(), 0.0001);

        BombState bothBombs = new BombState(true, true);
        tracker.updateEnvironment(bothBombs, 3 * SECOND);
        tracker.recordCraft(CraftingProfession.WOODWORKING, 220, recipe, bothBombs, 4 * SECOND);
        assertEquals(2, tracker.snapshot().xpSampleCount());
        assertEquals(210, tracker.snapshot().xpPerCraft().orElseThrow(), 0.0001);
    }

    @Test
    void speedBombHalvesMaterialsWithFloorButNotIngredients() {
        CraftingComponent firstMaterial = material("Cobalt Ingot", 2, 7);
        CraftingComponent secondMaterial = material("Dark Plank", 2, 4);
        CraftingComponent ingredient = ingredient("Strong Flesh", 2);

        assertEquals(3, firstMaterial.amountPerCraft(true));
        assertEquals(2, secondMaterial.amountPerCraft(true));
        assertEquals(2, ingredient.amountPerCraft(true));
        assertEquals(7, firstMaterial.amountPerCraft(false));
    }

    @Test
    void idleTimeoutHidesAndClearsTheRollingWindow() {
        CraftingStatsTracker tracker = tracker();
        CraftingRecipe recipe = recipe("Copper Ingot", "Oak Plank", "Rotten Flesh");
        tracker.recordCraft(CraftingProfession.ARMOURING, 100, recipe, BombState.NONE, 0);

        tracker.updateEnvironment(BombState.NONE, 300 * SECOND);
        assertFalse(tracker.snapshot().active());
        assertTrue(tracker.snapshot().xpPerCraft().isEmpty());
        assertEquals(CraftingProfession.ARMOURING, tracker.snapshot().profession().orElseThrow());
    }

    @Test
    void levelProjectionWaitsForTheProfessionModelToAdvance() {
        CraftingStatsTracker tracker = new CraftingStatsTracker(
                20, Duration.ofMinutes(5), Duration.ofSeconds(5));
        CraftingRecipe recipe = recipe("Copper Ingot", "Oak Plank", "Rotten Flesh");
        tracker.recordCraft(
                CraftingProfession.ARMOURING,
                100,
                100,
                73,
                recipe,
                BombState.NONE,
                0);
        assertTrue(tracker.snapshot().levelUpdatePending());

        tracker.updateEnvironment(BombState.NONE, 74, SECOND);
        assertFalse(tracker.snapshot().levelUpdatePending());
    }

    private CraftingStatsTracker tracker() {
        return new CraftingStatsTracker(20, Duration.ofMinutes(5));
    }

    private CraftingRecipe recipe(String firstMaterial, String secondMaterial, String ingredient) {
        return new CraftingRecipe(
                List.of(material(firstMaterial, 1, 6), material(secondMaterial, 1, 4)),
                List.of(ingredient(ingredient, 1)));
    }

    private CraftingComponent material(String name, int tier, int amount) {
        return new CraftingComponent(name, tier, amount, CraftingComponentKind.MATERIAL);
    }

    private CraftingComponent ingredient(String name, int amount) {
        return new CraftingComponent(name, 0, amount, CraftingComponentKind.INGREDIENT);
    }
}
