package com.rousoftware.wynngatheringstats.feature;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.core.consumers.features.Feature;
import com.wynntils.core.consumers.features.ProfileDefault;
import com.wynntils.core.consumers.overlays.Overlay;
import com.wynntils.core.consumers.overlays.annotations.RegisterOverlay;
import com.wynntils.core.persisted.config.Config;
import com.wynntils.core.persisted.config.Category;
import com.wynntils.core.persisted.config.ConfigCategory;
import com.wynntils.core.persisted.Persisted;
import com.wynntils.handlers.labels.event.LabelIdentifiedEvent;
import com.wynntils.handlers.labels.type.LabelInfo;
import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.mc.event.ContainerSetSlotEvent;
import com.wynntils.mc.event.CommandsAddedEvent;
import com.wynntils.mc.event.TickEvent;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.profession.event.ProfessionXpGainEvent;
import com.wynntils.models.profession.label.GatheringNodeHarvestLabelInfo;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.models.items.items.game.MaterialItem;
import com.wynntils.models.worlds.event.WorldStateEvent;
import com.wynntils.models.worlds.type.BombType;
import com.wynntils.utils.mc.McUtils;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.RenderElementType;
import com.rousoftware.wynngatheringstats.GatheringStatsClient;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceProvider;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceProviders;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceMode;
import com.rousoftware.wynngatheringstats.mixin.LabelInfoAccessor;
import com.rousoftware.wynngatheringstats.overlay.GatheringStatsOverlay;
import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.GatheringProfession;
import com.rousoftware.wynngatheringstats.stats.GatheringSnapshot;
import com.rousoftware.wynngatheringstats.stats.GatheringStatsTracker;
import com.rousoftware.wynngatheringstats.stats.HarvestLabelParseResult;
import com.rousoftware.wynngatheringstats.stats.HarvestMaterialLabelParser;
import com.rousoftware.wynngatheringstats.stats.LevelEstimate;
import com.rousoftware.wynngatheringstats.stats.LevelEstimator;
import com.rousoftware.wynngatheringstats.stats.MaterialKey;
import com.rousoftware.wynngatheringstats.stats.MaterialTierResolver;
import com.rousoftware.wynngatheringstats.stats.ProgressState;
import com.rousoftware.wynngatheringstats.stats.StatsDisplayConfig;
import com.rousoftware.wynngatheringstats.stats.TrackerSnapshot;
import java.util.Map;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@ConfigCategory(Category.OVERLAYS)
public final class GatheringStatsFeature extends Feature {
    private static final double EMERALDS_PER_LIQUID_EMERALD = 4_096d;
    private static final String MAPLE_PAPER_UNFORMATTED = "+6196 Ⓗ Woodcutting XP [32.2%]\n+1 Maple Paper";
    private static final String MAPLE_PAPER_FORMATTED =
            "§f+6196 §7Ⓗ Woodcutting XP §6[32.2%]\n§f+1 §7Maple Paper";
    private static boolean commandRegistered;

    private final GatheringStatsTracker tracker = new GatheringStatsTracker();
    private final MaterialTierResolver materialTierResolver = new MaterialTierResolver();
    private final MaterialPriceProvider materialPriceProvider = MaterialPriceProviders.create();
    private boolean debugCaptureEnabled;
    private LabelDiagnostic lastLabelDiagnostic;
    private MaterialItemDiagnostic lastMaterialItemDiagnostic;

    @Persisted
    private final Config<Integer> nodeWindowSize = new Config<>(GatheringStatsTracker.DEFAULT_WINDOW_SIZE);

    @Persisted
    private final Config<MaterialPriceMode> materialPriceMode = new Config<>(MaterialPriceMode.LOWEST);

    @Persisted
    private final Config<Boolean> showProfessionHeader = new Config<>(true);

    @Persisted
    private final Config<Boolean> showBombStatus = new Config<>(true);

    @Persisted
    private final Config<Boolean> showXpPerNode = new Config<>(true);

    @Persisted
    private final Config<Boolean> showSecondsPerNode = new Config<>(true);

    @Persisted
    private final Config<Boolean> showOneStarItemsPerHour = new Config<>(true);

    @Persisted
    private final Config<Boolean> showTwoStarItemsPerHour = new Config<>(true);

    @Persisted
    private final Config<Boolean> showThreeStarItemsPerHour = new Config<>(true);

    @Persisted
    private final Config<Boolean> showLePerHour = new Config<>(true);

    @Persisted
    private final Config<Boolean> showXpUntilLevel = new Config<>(true);

    @Persisted
    private final Config<Boolean> showTimeUntilLevel = new Config<>(true);

    @Persisted
    private final Config<Boolean> showNodesUntilLevel = new Config<>(true);

    @RegisterOverlay(renderType = RenderElementType.HOTBAR)
    private final Overlay gatheringStatsOverlay = new GatheringStatsOverlay(this::createSnapshot, this::displayConfig);

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
        resetSession();
    }

    @Override
    protected void onConfigUpdate(Config<?> config) {
        if (config != nodeWindowSize) {
            return;
        }

        int configuredSize = nodeWindowSize.get();
        if (configuredSize <= 0) {
            configuredSize = 1;
            nodeWindowSize.store(configuredSize);
        }
        tracker.setWindowSize(configuredSize);
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGatheringLabel(LabelIdentifiedEvent event) {
        LabelInfo labelInfo = event.getLabelInfo();
        if (!(labelInfo instanceof GatheringNodeHarvestLabelInfo gatheringLabel)) {
            capturePossibleGatheringLabel(labelInfo, "not a GatheringNodeHarvestLabelInfo");
            return;
        }

        String unformattedLabel = gatheringLabel.getName();
        String formattedLabel = getFormattedLabel(gatheringLabel);
        HarvestLabelParseResult result = HarvestMaterialLabelParser.analyze(unformattedLabel, formattedLabel);

        if (fromWynntils(gatheringLabel.getProfessionType()) == null) {
            saveLabelDiagnostic(gatheringLabel, result, "not a supported gathering profession");
            return;
        }

        try {
            if (!result.hasAmountAndMaterial()) {
                saveLabelDiagnostic(gatheringLabel, result, "not recorded: " + result.failureReason());
                return;
            }

            MaterialTierResolver.ResolvedMaterial resolved = materialTierResolver
                    .recordHarvestLabel(
                            result.materialName().orElseThrow(),
                            result.amount().getAsInt(),
                            result.tier(),
                            System.nanoTime())
                    .orElse(null);
            if (resolved == null) {
                saveLabelDiagnostic(gatheringLabel, result, "waiting for MaterialItem tier data");
                return;
            }

            recordResolvedMaterial(resolved, gatheringLabel, result, "recorded into the current node");
        } catch (RuntimeException exception) {
            saveLabelDiagnostic(gatheringLabel, result, "exception: " + exception.getClass().getSimpleName());
            GatheringStatsClient.LOGGER.error("Failed to record a gathered material", exception);
        }
    }

    // Wynntils annotates the incoming stack as a MaterialItem at HIGHEST priority. Its quality tier is
    // the authoritative replacement for the star data that Wynncraft no longer places in harvest labels.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerSetSlot(ContainerSetSlotEvent.Pre event) {
        inspectMaterialItem(event.getItemStack(), "slot update");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerSetContent(ContainerSetContentEvent.Pre event) {
        for (ItemStack itemStack : event.getItems()) {
            inspectMaterialItem(itemStack, "container update");
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
        resetSession();
    }

    @SubscribeEvent
    public void onCharacterUpdated(CharacterUpdateEvent event) {
        resetSession();
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
        OptionalDouble lePerHour = showLePerHour.get()
                ? calculateLePerHour(tracked.materialItemsPerHour())
                : OptionalDouble.empty();

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
                tracked.oneStarItemsPerHour(),
                tracked.twoStarItemsPerHour(),
                tracked.threeStarItemsPerHour(),
                materialPriceProvider.isAvailable(),
                lePerHour,
                state,
                xpUntilLevel,
                nodesUntilLevel,
                secondsUntilLevel);
    }

    private OptionalDouble calculateLePerHour(Map<MaterialKey, Double> rates) {
        if (!materialPriceProvider.isAvailable() || rates.isEmpty()) {
            return OptionalDouble.empty();
        }

        double emeraldsPerHour = 0;
        for (Map.Entry<MaterialKey, Double> entry : rates.entrySet()) {
            OptionalDouble unitPrice =
                    materialPriceProvider.getUnitPrice(
                            entry.getKey().name(), entry.getKey().tier(), materialPriceMode.get());
            if (unitPrice.isEmpty()) {
                return OptionalDouble.empty();
            }
            emeraldsPerHour += entry.getValue() * unitPrice.getAsDouble();
        }
        return OptionalDouble.of(emeraldsPerHour / EMERALDS_PER_LIQUID_EMERALD);
    }

    private StatsDisplayConfig displayConfig() {
        return new StatsDisplayConfig(
                showProfessionHeader.get(),
                showBombStatus.get(),
                showXpPerNode.get(),
                showSecondsPerNode.get(),
                showOneStarItemsPerHour.get(),
                showTwoStarItemsPerHour.get(),
                showThreeStarItemsPerHour.get(),
                showLePerHour.get(),
                showXpUntilLevel.get(),
                showTimeUntilLevel.get(),
                showNodesUntilLevel.get());
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
                    resetSession();
                    McUtils.sendWynntilsPrefixMessage(Component.literal("Gathering statistics reset."));
                    return 1;
                }))
                .then(Commands.literal("debug")
                        .executes(context -> sendDebugStatus())
                        .then(Commands.literal("status").executes(context -> sendDebugStatus()))
                        .then(Commands.literal("selftest").executes(context -> runDebugSelfTest()))
                        .then(Commands.literal("capture").executes(context -> setDebugCapture(true)))
                        .then(Commands.literal("capture-off").executes(context -> setDebugCapture(false)))
                        .then(Commands.literal("last").executes(context -> sendLastLabelDiagnostic())));
    }

    private int sendDebugStatus() {
        TrackerSnapshot snapshot = tracker.snapshot();
        debugMessage("capture=" + (debugCaptureEnabled ? "ON" : "OFF")
                + ", window=" + nodeWindowSize.get()
                + ", WynnVentory=" + (materialPriceProvider.isAvailable() ? "available" : "not detected"));
        debugMessage("item rows: ★=" + onOff(showOneStarItemsPerHour.get())
                + ", ★★=" + onOff(showTwoStarItemsPerHour.get())
                + ", ★★★=" + onOff(showThreeStarItemsPerHour.get()));
        debugMessage("tracker: profession=" + snapshot.profession().map(Enum::name).orElse("none")
                + ", XP samples=" + snapshot.xpSampleCount()
                + ", timing samples=" + snapshot.durationSampleCount()
                + ", material types=" + snapshot.materialItemsPerHour().size()
                + ", waiting labels=" + materialTierResolver.pendingHarvestCount()
                + ", waiting item tiers=" + materialTierResolver.pendingTierCount());
        debugMessage("rates: ★=" + formatRate(snapshot.oneStarItemsPerHour())
                + ", ★★=" + formatRate(snapshot.twoStarItemsPerHour())
                + ", ★★★=" + formatRate(snapshot.threeStarItemsPerHour()));
        if (lastLabelDiagnostic != null) {
            debugMessage("last label: " + lastLabelDiagnostic.summary());
        }
        if (lastMaterialItemDiagnostic != null) {
            debugMessage("last MaterialItem: " + lastMaterialItemDiagnostic.summary());
        }
        return 1;
    }

    private int runDebugSelfTest() {
        HarvestLabelParseResult result =
                HarvestMaterialLabelParser.analyze(MAPLE_PAPER_UNFORMATTED, MAPLE_PAPER_FORMATTED);
        GatheringStatsTracker diagnosticTracker = new GatheringStatsTracker(2);
        diagnosticTracker.recordNode(GatheringProfession.WOODCUTTING, 100, 10, 20, BombState.NONE, 0);
        if (result.hasAmountAndMaterial()) {
            diagnosticTracker.recordMaterial(
                    result.materialName().orElseThrow(),
                    2,
                    result.amount().getAsInt());
        }
        diagnosticTracker.recordNode(
                GatheringProfession.WOODCUTTING, 100, 11, 20, BombState.NONE, 10_000_000_000L);
        if (result.hasAmountAndMaterial()) {
            diagnosticTracker.recordMaterial(
                    result.materialName().orElseThrow(),
                    2,
                    result.amount().getAsInt());
        }

        TrackerSnapshot snapshot = diagnosticTracker.snapshot();
        debugMessage("Maple Paper self-test: amount=" + format(result.amount())
                + ", material=" + result.materialName().orElse("—")
                + ", label tier=" + format(result.tier())
                + " (expected: —; supplied by MaterialItem)");
        debugMessage("self-test tracker: ★=" + formatRate(snapshot.oneStarItemsPerHour())
                + ", ★★=" + formatRate(snapshot.twoStarItemsPerHour())
                + ", ★★★=" + formatRate(snapshot.threeStarItemsPerHour())
                + ", materials=" + snapshot.materialItemsPerHour());
        return result.hasAmountAndMaterial() && snapshot.twoStarItemsPerHour().isPresent() ? 1 : 0;
    }

    private int setDebugCapture(boolean enabled) {
        debugCaptureEnabled = enabled;
        debugMessage(enabled
                ? "Live label capture enabled. Gather one node, then run /gatherstats debug last."
                : "Live label capture disabled.");
        return 1;
    }

    private int sendLastLabelDiagnostic() {
        if (lastLabelDiagnostic == null) {
            debugMessage("No relevant label has been captured. Run /gatherstats debug capture, then gather one node.");
            return 0;
        }

        debugMessage(lastLabelDiagnostic.summary());
        debugMessage("raw=" + escapedAndTrimmed(lastLabelDiagnostic.unformattedLabel()));
        debugMessage("formatted=" + escapedAndTrimmed(lastLabelDiagnostic.formattedLabel()));
        if (lastMaterialItemDiagnostic != null) {
            debugMessage("MaterialItem=" + lastMaterialItemDiagnostic.summary());
        }
        return 1;
    }

    private void inspectMaterialItem(ItemStack itemStack, String source) {
        try {
            MaterialItem materialItem = Models.Item.asWynnItem(itemStack, MaterialItem.class).orElse(null);
            if (materialItem == null) {
                return;
            }

            String materialName = materialItem.getMaterialInfo().name();
            int tier = materialItem.getQualityTier();
            MaterialTierResolver.ResolvedMaterial resolved =
                    materialTierResolver.recordMaterialItem(materialName, tier, System.nanoTime()).orElse(null);
            lastMaterialItemDiagnostic = new MaterialItemDiagnostic(materialName, tier, source, resolved != null);
            if (resolved != null) {
                recordResolvedMaterial(resolved, null, null, "recorded from MaterialItem tier data");
            } else if (debugCaptureEnabled) {
                GatheringStatsClient.LOGGER.info(
                        "Gathering stats debug: {}", lastMaterialItemDiagnostic.summary());
            }
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to inspect a MaterialItem update", exception);
        }
    }

    private void recordResolvedMaterial(
            MaterialTierResolver.ResolvedMaterial resolved,
            GatheringNodeHarvestLabelInfo labelInfo,
            HarvestLabelParseResult parsedLabel,
            String successMessage) {
        boolean recorded = tracker.recordMaterial(resolved.materialName(), resolved.tier(), resolved.amount());
        if (labelInfo != null && parsedLabel != null) {
            HarvestLabelParseResult resolvedLabel = new HarvestLabelParseResult(
                    parsedLabel.amount(),
                    java.util.Optional.of(resolved.materialName()),
                    java.util.OptionalInt.of(resolved.tier()));
            saveLabelDiagnostic(labelInfo, resolvedLabel, recorded ? successMessage : "tier resolved but no node is active");
        } else if (lastLabelDiagnostic != null) {
            HarvestLabelParseResult resolvedLabel = new HarvestLabelParseResult(
                    lastLabelDiagnostic.parsed().amount(),
                    java.util.Optional.of(resolved.materialName()),
                    java.util.OptionalInt.of(resolved.tier()));
            lastLabelDiagnostic = new LabelDiagnostic(
                    lastLabelDiagnostic.labelType(),
                    lastLabelDiagnostic.unformattedLabel(),
                    lastLabelDiagnostic.formattedLabel(),
                    resolvedLabel,
                    recorded ? successMessage : "tier resolved but no node is active");
        }
    }

    private void capturePossibleGatheringLabel(LabelInfo labelInfo, String outcome) {
        if (!debugCaptureEnabled || !labelInfo.getName().contains("XP")) {
            return;
        }
        HarvestLabelParseResult result = HarvestMaterialLabelParser.analyze(labelInfo.getName(), getFormattedLabel(labelInfo));
        saveLabelDiagnostic(labelInfo, result, outcome);
    }

    private void saveLabelDiagnostic(LabelInfo labelInfo, HarvestLabelParseResult result, String outcome) {
        lastLabelDiagnostic = new LabelDiagnostic(
                labelInfo.getClass().getSimpleName(),
                labelInfo.getName(),
                getFormattedLabel(labelInfo),
                result,
                outcome);
        if (debugCaptureEnabled) {
            GatheringStatsClient.LOGGER.info("Gathering stats debug: {}", lastLabelDiagnostic.summary());
        }
    }

    private static String getFormattedLabel(LabelInfo labelInfo) {
        try {
            return ((LabelInfoAccessor) (Object) labelInfo).gatheringStats$getLabel().getString();
        } catch (RuntimeException exception) {
            return "<unavailable: " + exception.getClass().getSimpleName() + ">";
        }
    }

    private static void debugMessage(String message) {
        McUtils.sendWynntilsPrefixMessage(Component.literal("[GatherStats debug] " + message));
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static String formatRate(OptionalDouble value) {
        return value.isPresent() ? String.format(Locale.ROOT, "%.1f/hr", value.getAsDouble()) : "—";
    }

    private static String format(java.util.OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "—";
    }

    private static String escapedAndTrimmed(String text) {
        String escaped = text.replace("§", "\\\\u00A7").replace("\n", "\\\\n");
        return escaped.length() <= 220 ? escaped : escaped.substring(0, 217) + "...";
    }

    private void resetSession() {
        tracker.reset();
        materialTierResolver.reset();
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

    private record LabelDiagnostic(
            String labelType,
            String unformattedLabel,
            String formattedLabel,
            HarvestLabelParseResult parsed,
            String outcome) {
        private String summary() {
            return "type=" + labelType
                    + ", amount=" + format(parsed.amount())
                    + ", material=" + parsed.materialName().orElse("—")
                    + ", tier=" + format(parsed.tier())
                    + ", result=" + outcome;
        }
    }

    private record MaterialItemDiagnostic(String materialName, int tier, String source, boolean matchedLabel) {
        private String summary() {
            return "name=" + materialName + ", tier=" + tier + ", source=" + source
                    + ", matched label=" + matchedLabel;
        }
    }

}
