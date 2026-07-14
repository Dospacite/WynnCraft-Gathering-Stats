package com.rousoftware.wynngatheringstats.overlay;

import com.wynntils.core.consumers.overlays.OverlayPosition;
import com.wynntils.core.consumers.overlays.OverlaySize;
import com.wynntils.core.consumers.overlays.TextOverlay;
import com.wynntils.utils.render.type.HorizontalAlignment;
import com.wynntils.utils.render.type.VerticalAlignment;
import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.GatheringSnapshot;
import com.rousoftware.wynngatheringstats.stats.ProgressState;
import com.rousoftware.wynngatheringstats.stats.StatsFormatter;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Supplier;

public final class GatheringStatsOverlay extends TextOverlay {
    private final Supplier<GatheringSnapshot> snapshotSupplier;

    public GatheringStatsOverlay(Supplier<GatheringSnapshot> snapshotSupplier) {
        super(
                new OverlayPosition(
                        6,
                        6,
                        VerticalAlignment.TOP,
                        HorizontalAlignment.LEFT,
                        OverlayPosition.AnchorSection.TOP_LEFT),
                new OverlaySize(180, 68),
                HorizontalAlignment.LEFT,
                VerticalAlignment.TOP);
        this.snapshotSupplier = snapshotSupplier;
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
                ProgressState.READY,
                OptionalLong.of(45678),
                OptionalLong.of(38),
                OptionalDouble.of(282));
        return format(preview);
    }

    private String format(GatheringSnapshot snapshot) {
        String level = snapshot.level() > 0 ? Integer.toString(snapshot.level()) : StatsFormatter.UNAVAILABLE;
        String header = "&b" + snapshot.profession().displayName() + " &7Lv. &f" + level + bombSuffix(snapshot.bombState());
        String xpPerNode = StatsFormatter.decimal(snapshot.xpPerNode());
        String secondsPerNode = StatsFormatter.decimal(snapshot.secondsPerNode());
        String xpUntilLevel = StatsFormatter.levelMetric(snapshot.progressState(), snapshot.xpUntilLevel());
        String timeUntilLevel = StatsFormatter.levelDuration(snapshot.progressState(), snapshot.secondsUntilLevel());
        String nodesUntilLevel = StatsFormatter.levelMetric(snapshot.progressState(), snapshot.nodesUntilLevel());

        return String.join(
                "\n",
                header,
                row("XP / node", xpPerNode),
                row("Seconds / node", secondsPerNode),
                row("XP until level", xpUntilLevel),
                row("Time until level", timeUntilLevel),
                row("Nodes until level", nodesUntilLevel));
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
}
