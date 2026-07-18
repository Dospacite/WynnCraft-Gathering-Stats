package com.rousoftware.wynngatheringstats.overlay;

import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.CombatSnapshot;
import com.rousoftware.wynngatheringstats.stats.CraftingComponent;
import com.rousoftware.wynngatheringstats.stats.CraftingComponentKind;
import com.rousoftware.wynngatheringstats.stats.CraftingComponentProjection;
import com.rousoftware.wynngatheringstats.stats.CraftingProfession;
import com.rousoftware.wynngatheringstats.stats.CraftingSnapshot;
import com.rousoftware.wynngatheringstats.stats.GatheringSnapshot;
import com.rousoftware.wynngatheringstats.stats.ProgressState;
import com.rousoftware.wynngatheringstats.stats.StatsDisplayConfig;
import com.rousoftware.wynngatheringstats.stats.StatsFormatter;
import com.wynntils.core.consumers.overlays.OverlayPosition;
import com.wynntils.core.consumers.overlays.OverlaySize;
import com.wynntils.core.consumers.overlays.TextOverlay;
import com.wynntils.utils.render.type.HorizontalAlignment;
import com.wynntils.utils.render.type.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** One configurable overlay element that swaps between gathering, crafting, and combat statistics. */
public final class GatheringStatsOverlay extends TextOverlay {
    private final Supplier<GatheringSnapshot> gatheringSnapshotSupplier;
    private final Supplier<CraftingSnapshot> craftingSnapshotSupplier;
    private final Supplier<CombatSnapshot> combatSnapshotSupplier;
    private final Supplier<StatsDisplayConfig> configSupplier;

    public GatheringStatsOverlay(
            Supplier<GatheringSnapshot> gatheringSnapshotSupplier,
            Supplier<CraftingSnapshot> craftingSnapshotSupplier,
            Supplier<CombatSnapshot> combatSnapshotSupplier,
            Supplier<StatsDisplayConfig> configSupplier) {
        super(
                new OverlayPosition(
                        -2,
                        -1,
                        VerticalAlignment.MIDDLE,
                        HorizontalAlignment.RIGHT,
                        OverlayPosition.AnchorSection.MIDDLE_RIGHT),
                new OverlaySize(230, 140),
                HorizontalAlignment.RIGHT,
                VerticalAlignment.MIDDLE);
        this.gatheringSnapshotSupplier = gatheringSnapshotSupplier;
        this.craftingSnapshotSupplier = craftingSnapshotSupplier;
        this.combatSnapshotSupplier = combatSnapshotSupplier;
        this.configSupplier = configSupplier;
    }

    @Override
    public String getTranslatedName() {
        return "WynnCraft Profession Stats";
    }

    @Override
    public String getTemplate() {
        GatheringSnapshot gathering = gatheringSnapshotSupplier.get();
        if (gathering.active()) {
            return formatGathering(gathering, configSupplier.get());
        }

        CombatSnapshot combat = combatSnapshotSupplier.get();
        if (combat.active()) {
            return formatCombat(combat);
        }

        CraftingSnapshot crafting = craftingSnapshotSupplier.get();
        return crafting.active() ? formatCrafting(crafting) : "";
    }

    @Override
    public String getPreviewTemplate() {
        List<CraftingComponentProjection> components = List.of(
                new CraftingComponentProjection(
                        new CraftingComponent("Cobalt Ingot", 2, 6, CraftingComponentKind.MATERIAL),
                        1_482,
                        OptionalDouble.of(25_600)),
                new CraftingComponentProjection(
                        new CraftingComponent("Dark Plank", 2, 4, CraftingComponentKind.MATERIAL),
                        988,
                        OptionalDouble.of(3_200)),
                new CraftingComponentProjection(
                        new CraftingComponent("Strong Flesh", 0, 2, CraftingComponentKind.INGREDIENT),
                        494,
                        OptionalDouble.of(8_192)));
        return formatCrafting(new CraftingSnapshot(
                true,
                CraftingProfession.WEAPONSMITHING,
                73,
                80,
                new BombState(true, true),
                OptionalDouble.of(2_345.6),
                ProgressState.READY,
                OptionalLong.of(579_321),
                OptionalLong.of(247),
                true,
                components));
    }

    private static String formatGathering(GatheringSnapshot snapshot, StatsDisplayConfig config) {
        String level = snapshot.level() > 0 ? Integer.toString(snapshot.level()) : StatsFormatter.UNAVAILABLE;
        List<String> lines = new ArrayList<>();

        if (config.showProfessionHeader()) {
            String bombs = config.showBombStatus() ? bombSuffix(snapshot.bombState()) : "";
            lines.add("&b" + snapshot.profession().displayName() + " &7Lv. &f" + level + bombs);
        }
        addRow(lines, config.showXpPerNode(), "XP / node", StatsFormatter.decimal(snapshot.xpPerNode()));
        addRow(
                lines,
                config.showSecondsPerNode(),
                "Seconds / node",
                StatsFormatter.decimal(snapshot.secondsPerNode()));
        addRow(
                lines,
                config.showOneStarItemsPerHour(),
                "★ Items / HR",
                StatsFormatter.decimal(snapshot.oneStarItemsPerHour()));
        addRow(
                lines,
                config.showTwoStarItemsPerHour(),
                "★★ Items / HR",
                StatsFormatter.decimal(snapshot.twoStarItemsPerHour()));
        addRow(
                lines,
                config.showThreeStarItemsPerHour(),
                "★★★ Items / HR",
                StatsFormatter.decimal(snapshot.threeStarItemsPerHour()));
        addRow(
                lines,
                config.showLePerHour() && snapshot.tradeMarketAvailable(),
                "LE / HR",
                StatsFormatter.decimal(snapshot.lePerHour()));
        addRow(
                lines,
                config.showXpUntilLevel(),
                "XP until level",
                StatsFormatter.levelMetric(snapshot.progressState(), snapshot.xpUntilLevel()));
        addRow(
                lines,
                config.showTimeUntilLevel(),
                "Time until level",
                StatsFormatter.levelDuration(snapshot.progressState(), snapshot.secondsUntilLevel()));
        addRow(
                lines,
                config.showNodesUntilLevel(),
                "Nodes until level",
                StatsFormatter.levelMetric(snapshot.progressState(), snapshot.nodesUntilLevel()));
        return String.join("\n", lines);
    }

    private static String formatCrafting(CraftingSnapshot snapshot) {
        String level = snapshot.level() > 0 ? Integer.toString(snapshot.level()) : StatsFormatter.UNAVAILABLE;
        List<String> lines = new ArrayList<>();
        lines.add("&b" + snapshot.profession().displayName() + " &7Lv. &f" + level + bombSuffix(snapshot.bombState()));
        lines.add(row("XP / craft", StatsFormatter.decimal(snapshot.xpPerCraft())));
        lines.add(row(
                "XP until level " + snapshot.targetLevel(),
                StatsFormatter.levelMetric(snapshot.progressState(), snapshot.xpUntilTarget())));
        lines.add(row(
                "Crafts until level " + snapshot.targetLevel(),
                StatsFormatter.levelMetric(snapshot.progressState(), snapshot.craftsUntilTarget())));
        lines.add("&7Items until level " + snapshot.targetLevel() + ":");
        if (snapshot.components().isEmpty()) {
            lines.add("&7  &f" + StatsFormatter.levelMetric(snapshot.progressState(), OptionalLong.empty()));
        } else {
            snapshot.components().forEach(component -> lines.add(componentRow(component, snapshot.tradeMarketAvailable())));
        }
        return String.join("\n", lines);
    }

    private static String formatCombat(CombatSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(row("XP / HR", StatsFormatter.decimal(snapshot.xpPerHour())));
        lines.add(row("Items / HR", StatsFormatter.decimal(snapshot.itemsPerHour())));
        lines.add(row(
                "XP until level up",
                StatsFormatter.levelMetric(snapshot.progressState(), snapshot.xpUntilLevel())));
        lines.add(row(
                "Time until level",
                StatsFormatter.levelDuration(snapshot.progressState(), snapshot.secondsUntilLevel())));
        lines.add(row("XP per kill", StatsFormatter.decimal(snapshot.xpPerKill())));
        lines.add(row("Kills per HR", StatsFormatter.decimal(snapshot.killsPerHour())));
        if (snapshot.tradeMarketAvailable()) {
            lines.add(row("LE / HR", StatsFormatter.decimal(snapshot.lePerHour())));
        }
        return String.join("\n", lines);
    }

    private static String componentRow(CraftingComponentProjection projection, boolean showCost) {
        CraftingComponent component = projection.component();
        String stars = component.kind() == CraftingComponentKind.MATERIAL ? " " + "★".repeat(component.tier()) : "";
        String cost = showCost ? " &2(" + StatsFormatter.marketCost(projection.emeraldCost()) + ")" : "";
        return "&7  " + component.name() + stars + ": &f" + StatsFormatter.integer(projection.requiredAmount()) + cost;
    }

    private static String bombSuffix(BombState state) {
        StringBuilder suffix = new StringBuilder();
        if (state.professionXpActive()) {
            suffix.append(" &6XP ×2");
        }
        if (state.professionSpeedActive()) {
            suffix.append(" &aSpeed ×2");
        }
        return suffix.toString();
    }

    private static String row(String label, String value) {
        return "&7" + label + ": &f" + value;
    }

    private static void addRow(List<String> lines, boolean shown, String label, String value) {
        if (shown) {
            lines.add(row(label, value));
        }
    }
}
