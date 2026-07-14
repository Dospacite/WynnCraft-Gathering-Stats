package com.rousoftware.wynngatheringstats.feature;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.consumers.features.ProfileDefault;
import com.wynntils.core.consumers.overlays.Overlay;
import com.wynntils.core.consumers.overlays.annotations.RegisterOverlay;
import com.wynntils.core.persisted.config.Category;
import com.wynntils.core.persisted.config.ConfigCategory;
import com.wynntils.mc.event.CommandsAddedEvent;
import com.wynntils.mc.event.TickEvent;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.profession.event.ProfessionXpGainEvent;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.worlds.event.WorldStateEvent;
import com.wynntils.models.worlds.type.BombType;
import com.wynntils.utils.mc.McUtils;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.RenderElementType;
import com.rousoftware.wynngatheringstats.GatheringStatsClient;
import com.rousoftware.wynngatheringstats.overlay.GatheringStatsOverlay;
import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.GatheringProfession;
import com.rousoftware.wynngatheringstats.stats.GatheringSnapshot;
import com.rousoftware.wynngatheringstats.stats.GatheringStatsTracker;
import com.rousoftware.wynngatheringstats.stats.LevelEstimate;
import com.rousoftware.wynngatheringstats.stats.LevelEstimator;
import com.rousoftware.wynngatheringstats.stats.ProgressState;
import com.rousoftware.wynngatheringstats.stats.TrackerSnapshot;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@ConfigCategory(Category.OVERLAYS)
public final class GatheringStatsFeature extends Feature {
    private static boolean commandRegistered;

    private final GatheringStatsTracker tracker = new GatheringStatsTracker();

    @RegisterOverlay(renderType = RenderElementType.HOTBAR)
    private final Overlay gatheringStatsOverlay = new GatheringStatsOverlay(this::createSnapshot);

    public GatheringStatsFeature() {
        super(ProfileDefault.ENABLED);
    }

    @Override
    public String getTranslatedName() {
        return "WynnCraft Gathering Stats";
    }

    @Override
    public String getTranslatedDescription() {
        return "Tracks gathering efficiency and estimates progress to the next profession level.";
    }

    @Override
    public void onEnable() {
        registerCommandOnce();
    }

    @Override
    public void onDisable() {
        tracker.reset();
    }

    // Wynntils' XP Gain Message feature cancels this event when it filters the original chat message.
    // We still need the observation after ProfessionModel has updated its state.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onProfessionXpGain(ProfessionXpGainEvent event) {
        GatheringProfession profession = fromWynntils(event.getProfession());
        if (profession == null) {
            return;
        }

        try {
            tracker.recordNode(
                    profession,
                    event.getGainedXpRaw(),
                    event.getCurrentXpPercentage(),
                    Models.Profession.getLevel(event.getProfession()),
                    currentBombState(),
                    System.nanoTime());
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to record a gathering node", exception);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        try {
            TrackerSnapshot snapshot = tracker.snapshot();
            int level = snapshot.profession()
                    .map(GatheringStatsFeature::toWynntils)
                    .map(Models.Profession::getLevel)
                    .orElse(0);
            tracker.updateEnvironment(currentBombState(), level, System.nanoTime());
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to update the gathering environment", exception);
        }
    }

    @SubscribeEvent
    public void onWorldStateChanged(WorldStateEvent event) {
        tracker.reset();
    }

    @SubscribeEvent
    public void onCharacterUpdated(CharacterUpdateEvent event) {
        tracker.reset();
    }

    @SubscribeEvent
    public void onCommandsAdded(CommandsAddedEvent event) {
        Managers.Command.addNode(event.getRoot(), createCommand().build());
    }

    private GatheringSnapshot createSnapshot() {
        TrackerSnapshot tracked = tracker.snapshot();
        if (tracked.profession().isEmpty()) {
            return GatheringSnapshot.inactive(tracked.bombState());
        }

        GatheringProfession profession = tracked.profession().orElseThrow();
        ProfessionType professionType = toWynntils(profession);
        int level = Models.Profession.getLevel(professionType);
        ProgressState state;
        if (level >= GatheringStatsTracker.MAX_PROFESSION_LEVEL) {
            state = ProgressState.MAX_LEVEL;
        } else if (tracked.levelUpdatePending()) {
            state = ProgressState.UPDATING;
        } else if (level <= 0) {
            state = ProgressState.SYNCING;
        } else {
            state = ProgressState.READY;
        }

        OptionalLong xpUntilLevel = OptionalLong.empty();
        OptionalLong nodesUntilLevel = OptionalLong.empty();
        OptionalDouble secondsUntilLevel = OptionalDouble.empty();

        if (state == ProgressState.READY) {
            CappedValue xp = Models.Profession.getXP(professionType);
            long remaining = Math.max(0, xp.getRemaining());
            xpUntilLevel = OptionalLong.of(remaining);
            LevelEstimate estimate = LevelEstimator.estimate(remaining, tracked.xpPerNode(), tracked.secondsPerNode());
            nodesUntilLevel = estimate.nodes();
            secondsUntilLevel = estimate.seconds();
        }

        return new GatheringSnapshot(
                true,
                profession,
                level,
                tracked.bombState(),
                tracked.xpPerNode(),
                tracked.secondsPerNode(),
                state,
                xpUntilLevel,
                nodesUntilLevel,
                secondsUntilLevel);
    }

    private void registerCommandOnce() {
        synchronized (GatheringStatsFeature.class) {
            if (commandRegistered) {
                return;
            }
            Managers.Command.addNodeToClientDispatcher(createCommand());
            commandRegistered = true;
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("gatherstats")
                .then(Commands.literal("reset").executes(context -> {
                    tracker.reset();
                    McUtils.sendWynntilsPrefixMessage(Component.literal("Gathering statistics reset."));
                    return 1;
                }));
    }

    private static BombState currentBombState() {
        return new BombState(
                Models.Bomb.isBombActive(BombType.PROFESSION_XP),
                Models.Bomb.isBombActive(BombType.PROFESSION_SPEED));
    }

    private static GatheringProfession fromWynntils(ProfessionType profession) {
        return switch (profession) {
            case WOODCUTTING -> GatheringProfession.WOODCUTTING;
            case MINING -> GatheringProfession.MINING;
            case FISHING -> GatheringProfession.FISHING;
            case FARMING -> GatheringProfession.FARMING;
            default -> null;
        };
    }

    private static ProfessionType toWynntils(GatheringProfession profession) {
        return switch (profession) {
            case WOODCUTTING -> ProfessionType.WOODCUTTING;
            case MINING -> ProfessionType.MINING;
            case FISHING -> ProfessionType.FISHING;
            case FARMING -> ProfessionType.FARMING;
        };
    }
}
