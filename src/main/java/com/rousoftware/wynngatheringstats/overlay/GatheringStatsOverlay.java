package com.rousoftware.wynngatheringstats.overlay;

import com.wynntils.core.consumers.overlays.OverlayPosition;
import com.wynntils.core.consumers.overlays.OverlaySize;
import com.wynntils.core.consumers.overlays.TextOverlay;
import com.wynntils.utils.render.type.HorizontalAlignment;
import com.wynntils.utils.render.type.VerticalAlignment;
import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.GatheringSnapshot;
import com.rousoftware.wynngatheringstats.stats.ProgressState;
import com.rousoftware.wynngatheringstats.stats.StatsDisplayConfig;
import com.rousoftware.wynngatheringstats.stats.StatsFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class GatheringStatsOverlay extends TextOverlay {
    private final Supplier<GatheringSnapshot> snapshotSupplier;
    private final Supplier<StatsDisplayConfig> configSupplier;

    public GatheringStatsOverlay(
            Supplier<GatheringSnapshot> snapshotSupplier, Supplier<StatsDisplayConfig> configSupplier) {
        super(
                new OverlayPosition(
                        6,
                        6,
                        VerticalAlignment.TOP,
                        HorizontalAlignment.LEFT,
                        OverlayPosition.AnchorSection.TOP_LEFT),
                new OverlaySize(190, 116),
                HorizontalAlignment.LEFT,
                VerticalAlignment.TOP);
        this.snapshotSupplier = snapshotSupplier;
        this.configSupplier = configSupplier;
    }

    @Override
    public String getTranslatedName() {
        return "WynnCraft Gathering Stats";
    }

    @Override
    public String getTemplate() {
        GatheringSnapshot snapshot = snapshotSupplier.get();
        return snapshot.active() ? format(snapshot) : "";
    }

    @Override
    public String getPreviewTemplate() {
        GatheringSnapshot preview = new GatheringSnapshot(
                true,
                com.rousoftware.wynngatheringstats.stats.GatheringProfession.MINING,
                87,
                new BombState(true, true),
                OptionalDouble.of(1234.5),
                OptionalDouble.of(7.4),
                OptionalDouble.of(486.5),
                OptionalDouble.of(42.2),
                OptionalDouble.of(7.8),
                true,
                OptionalDouble.of(13.7),
                ProgressState.READY,
                OptionalLong.of(45678),
                OptionalLong.of(38),
                OptionalDouble.of(282));
        return format(preview);
    }

    private String format(GatheringSnapshot snapshot) {
        StatsDisplayConfig config = configSupplier.get();
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

    private String bombSuffix(BombState state) {
        StringBuilder suffix = new StringBuilder();
        if (state.professionXpActive()) {
            suffix.append(" &6XP ×2");
        }
        if (state.professionSpeedActive()) {
            suffix.append(" &aSpeed ×2");
        }
        return suffix.toString();
    }

    private String row(String label, String value) {
        return "&7" + label + ": &f" + value;
    }

    private void addRow(List<String> lines, boolean shown, String label, String value) {
        if (shown) {
            lines.add(row(label, value));
        }
    }
}
