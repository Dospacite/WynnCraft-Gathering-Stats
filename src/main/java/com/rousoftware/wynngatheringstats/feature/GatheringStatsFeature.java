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
import com.wynntils.mc.event.ContainerClickEvent;
import com.wynntils.mc.event.ContainerSetContentEvent;
import com.wynntils.mc.event.ContainerSetSlotEvent;
import com.wynntils.mc.event.CommandsAddedEvent;
import com.wynntils.mc.event.TickEvent;
import com.wynntils.models.character.event.CharacterUpdateEvent;
import com.wynntils.models.characterstats.event.CombatXpGainEvent;
import com.wynntils.models.combat.label.KillLabelInfo;
import com.wynntils.models.containers.containers.CraftingStationContainer;
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
import com.rousoftware.wynngatheringstats.compat.CraftingRecipeCapture;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceProvider;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceProviders;
import com.rousoftware.wynngatheringstats.compat.MaterialPriceMode;
import com.rousoftware.wynngatheringstats.compat.MaterialValueCalculator;
import com.rousoftware.wynngatheringstats.compat.WynntilsItemNames;
import com.rousoftware.wynngatheringstats.event.CombatItemPickupEvent;
import com.rousoftware.wynngatheringstats.mixin.LabelInfoAccessor;
import com.rousoftware.wynngatheringstats.mixin.ProfessionModelAccessor;
import com.rousoftware.wynngatheringstats.overlay.GatheringStatsOverlay;
import com.rousoftware.wynngatheringstats.stats.BombState;
import com.rousoftware.wynngatheringstats.stats.CombatSnapshot;
import com.rousoftware.wynngatheringstats.stats.CombatStatsTracker;
import com.rousoftware.wynngatheringstats.stats.CombatTrackerSnapshot;
import com.rousoftware.wynngatheringstats.stats.CraftingComponent;
import com.rousoftware.wynngatheringstats.stats.CraftingComponentKind;
import com.rousoftware.wynngatheringstats.stats.CraftingComponentProjection;
import com.rousoftware.wynngatheringstats.stats.CraftingProfession;
import com.rousoftware.wynngatheringstats.stats.CraftingRecipe;
import com.rousoftware.wynngatheringstats.stats.CraftingSnapshot;
import com.rousoftware.wynngatheringstats.stats.CraftingStatsTracker;
import com.rousoftware.wynngatheringstats.stats.CraftingTrackerSnapshot;
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
import com.rousoftware.wynngatheringstats.stats.ProfessionTargetCalculator;
import com.rousoftware.wynngatheringstats.stats.StatsDisplayConfig;
import com.rousoftware.wynngatheringstats.stats.TrackerSnapshot;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@ConfigCategory(Category.OVERLAYS)
public final class GatheringStatsFeature extends Feature {
    private static final double EMERALDS_PER_LIQUID_EMERALD = 4_096d;
    private static final long PENDING_CRAFT_TIMEOUT_NANOS = 5_000_000_000L;
    private static final String MAPLE_PAPER_UNFORMATTED = "+6196 Ⓗ Woodcutting XP [32.2%]\n+1 Maple Paper";
    private static final String MAPLE_PAPER_FORMATTED =
            "§f+6196 §7Ⓗ Woodcutting XP §6[32.2%]\n§f+1 §7Maple Paper";
    private static boolean commandRegistered;

    private final GatheringStatsTracker tracker = new GatheringStatsTracker();
    private final CraftingStatsTracker craftingTracker = new CraftingStatsTracker();
    private final CombatStatsTracker combatTracker = new CombatStatsTracker();
    private final CraftingRecipeCapture craftingRecipeCapture = new CraftingRecipeCapture();
    private final MaterialTierResolver materialTierResolver = new MaterialTierResolver();
    private final MaterialPriceProvider materialPriceProvider = MaterialPriceProviders.create();
    private final WynntilsItemNames itemNames = new WynntilsItemNames();
    private boolean debugCaptureEnabled;
    private LabelDiagnostic lastLabelDiagnostic;
    private MaterialItemDiagnostic lastMaterialItemDiagnostic;
    private PendingCraftXp pendingCraftXp;

    @Persisted
    private final Config<Integer> nodeWindowSize = new Config<>(GatheringStatsTracker.DEFAULT_WINDOW_SIZE);

    @Persisted
    private final Config<Integer> craftingWindowSize = new Config<>(CraftingStatsTracker.DEFAULT_WINDOW_SIZE);

    @Persisted
    private final Config<Integer> inactivityTimeoutMinutes = new Config<>(5);

    @Persisted
    private final Config<Boolean> enableCombatTracking = new Config<>(true);

    @Persisted
    private final Config<String> trackedItemName = new Config<>("");

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
    private final Overlay statsOverlay = new GatheringStatsOverlay(
            this::createSnapshot, this::createCraftingSnapshot, this::createCombatSnapshot, this::displayConfig);

    public GatheringStatsFeature() {
        super(ProfileDefault.ENABLED);
    }

    @Override
    public String getTranslatedName() {
        return "WynnCraft Gathering Stats";
    }

    @Override
    public String getTranslatedDescription() {
        return "Tracks gathering, crafting, and combat efficiency and estimates profession progress.";
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
        if (config == inactivityTimeoutMinutes) {
            int configuredMinutes = inactivityTimeoutMinutes.get();
            if (configuredMinutes <= 0) {
                configuredMinutes = 1;
                inactivityTimeoutMinutes.store(configuredMinutes);
            }
            Duration timeout = Duration.ofMinutes(configuredMinutes);
            tracker.setIdleTimeout(timeout);
            craftingTracker.setIdleTimeout(timeout);
            combatTracker.setIdleTimeout(timeout);
            return;
        }
        if (config == enableCombatTracking) {
            if (!enableCombatTracking.get()) {
                combatTracker.reset();
            }
            return;
        }
        if (config == trackedItemName) {
            combatTracker.reset();
            return;
        }
        if (config == craftingWindowSize) {
            int configuredSize = craftingWindowSize.get();
            if (configuredSize <= 0) {
                configuredSize = 1;
                craftingWindowSize.store(configuredSize);
            }
            craftingTracker.setWindowSize(configuredSize);
            return;
        }
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
        GatheringProfession gatheringProfession = fromWynntils(event.getProfession());
        CraftingProfession craftingProfession = craftingFromWynntils(event.getProfession());
        if (gatheringProfession == null && craftingProfession == null) {
            return;
        }

        try {
            long nowNanos = System.nanoTime();
            BombState bombs = currentBombState();
            if (gatheringProfession != null) {
                tracker.recordNode(
                        gatheringProfession,
                        event.getGainedXpRaw(),
                        event.getCurrentXpPercentage(),
                        Models.Profession.getLevel(event.getProfession()),
                        bombs,
                        nowNanos);
                return;
            }

            PendingCraftXp pending = new PendingCraftXp(
                    craftingProfession,
                    event.getGainedXpRaw(),
                    event.getCurrentXpPercentage(),
                    Models.Profession.getLevel(event.getProfession()),
                    bombs,
                    nowNanos);
            if (!tryRecordCraft(pending, nowNanos)) {
                pendingCraftXp = pending;
            }
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to record profession XP", exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerClick(ContainerClickEvent event) {
        if (!(Models.Container.getCurrentContainer() instanceof CraftingStationContainer station)) {
            return;
        }
        CraftingProfession profession = craftingFromWynntils(station.getProfessionType());
        if (profession != null) {
            craftingRecipeCapture.captureBeforeClick(
                    profession,
                    event.getContainerMenu(),
                    currentBombState().professionSpeedActive(),
                    System.nanoTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onCombatXpGain(CombatXpGainEvent event) {
        if (!enableCombatTracking.get()) {
            return;
        }

        try {
            combatTracker.recordXpGain(event.getGainedXpRaw(), System.nanoTime());
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to record combat XP", exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCombatKillLabel(LabelIdentifiedEvent event) {
        if (!enableCombatTracking.get() || !(event.getLabelInfo() instanceof KillLabelInfo killLabel)) {
            return;
        }

        try {
            combatTracker.recordKill(killLabel.getCombatXp(), System.nanoTime());
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to record a combat kill", exception);
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCombatItemPickup(CombatItemPickupEvent event) {
        if (!enableCombatTracking.get()) {
            return;
        }

        itemNames.observe(event.getItemStack()).ifPresent(observedName -> {
            String selectedItem = trackedItemName.get();
            if (!selectedItem.isBlank() && WynntilsItemNames.matches(selectedItem, observedName)) {
                combatTracker.recordItemPickup(event.getAmount(), System.nanoTime());
            }
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        try {
            long nowNanos = System.nanoTime();
            BombState bombs = currentBombState();
            finishPendingCraft(nowNanos);
            TrackerSnapshot snapshot = tracker.snapshot();
            int level = snapshot.profession()
                    .map(GatheringStatsFeature::toWynntils)
                    .map(Models.Profession::getLevel)
                    .orElse(0);
            tracker.updateEnvironment(bombs, level, nowNanos);
            CraftingTrackerSnapshot craftingSnapshot = craftingTracker.snapshot();
            int craftingLevel = craftingSnapshot.profession()
                    .map(GatheringStatsFeature::toWynntils)
                    .map(Models.Profession::getLevel)
                    .orElse(0);
            craftingTracker.updateEnvironment(bombs, craftingLevel, nowNanos);
            if (enableCombatTracking.get()) {
                combatTracker.update(nowNanos);
            }
        } catch (RuntimeException exception) {
            GatheringStatsClient.LOGGER.error("Failed to update statistics", exception);
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
        if (!tracked.active()) {
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

    private CraftingSnapshot createCraftingSnapshot() {
        CraftingTrackerSnapshot tracked = craftingTracker.snapshot();
        if (!tracked.active()
                || tracker.snapshot().active()
                || (enableCombatTracking.get() && combatTracker.snapshot().active())) {
            return CraftingSnapshot.inactive(tracked.bombState(), materialPriceProvider.isAvailable());
        }

        CraftingProfession profession = tracked.profession().orElseThrow();
        ProfessionType professionType = toWynntils(profession);
        int level = Models.Profession.getLevel(professionType);
        int targetLevel = ProfessionTargetCalculator.nextTenthLevel(
                level, GatheringStatsTracker.MAX_PROFESSION_LEVEL);
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

        OptionalLong xpUntilTarget = OptionalLong.empty();
        OptionalLong craftsUntilTarget = OptionalLong.empty();
        List<CraftingComponentProjection> components = List.of();
        if (state == ProgressState.READY) {
            CappedValue xp = Models.Profession.getXP(professionType);
            xpUntilTarget = ProfessionTargetCalculator.xpUntilTarget(
                    level,
                    targetLevel,
                    Math.max(0, xp.getRemaining()),
                    ProfessionModelAccessor.gatheringStats$getLevelUpXpRequirements());
            if (xpUntilTarget.isPresent()) {
                craftsUntilTarget = LevelEstimator.estimate(
                                xpUntilTarget.getAsLong(), tracked.xpPerCraft(), OptionalDouble.empty())
                        .nodes();
            }
            if (craftsUntilTarget.isPresent()) {
                components = projectCraftingComponents(
                        tracked.recipe().orElseThrow(),
                        craftsUntilTarget.getAsLong(),
                        tracked.bombState().professionSpeedActive());
            }
        }

        return new CraftingSnapshot(
                true,
                profession,
                level,
                targetLevel,
                tracked.bombState(),
                tracked.xpPerCraft(),
                state,
                xpUntilTarget,
                craftsUntilTarget,
                materialPriceProvider.isAvailable(),
                components);
    }

    private CombatSnapshot createCombatSnapshot() {
        if (!enableCombatTracking.get() || tracker.snapshot().active()) {
            return CombatSnapshot.inactive(materialPriceProvider.isAvailable());
        }

        CombatTrackerSnapshot tracked = combatTracker.snapshot();
        if (!tracked.active()) {
            return CombatSnapshot.inactive(materialPriceProvider.isAvailable());
        }

        ProgressState state;
        int combatLevel = Models.CombatXp.getCombatLevel().current();
        if (Models.CombatXp.getCombatLevel().isAtCap()) {
            state = ProgressState.MAX_LEVEL;
        } else if (combatLevel <= 0) {
            state = ProgressState.SYNCING;
        } else {
            state = ProgressState.READY;
        }

        OptionalLong xpUntilLevel = OptionalLong.empty();
        OptionalDouble secondsUntilLevel = OptionalDouble.empty();
        if (state == ProgressState.READY) {
            long remainingXp = Math.max(0, Models.CombatXp.getXpPointsNeededToLevelUp());
            xpUntilLevel = OptionalLong.of(remainingXp);
            if (tracked.xpPerHour().isPresent() && tracked.xpPerHour().getAsDouble() > 0) {
                secondsUntilLevel = OptionalDouble.of(
                        remainingXp * 3_600d / tracked.xpPerHour().getAsDouble());
            }
        }

        OptionalDouble itemsPerHour = trackedItemName.get().isBlank()
                ? OptionalDouble.empty()
                : tracked.itemsPerHour();
        OptionalDouble lePerHour = calculateCombatLePerHour(itemsPerHour);

        return new CombatSnapshot(
                true,
                tracked.xpPerHour(),
                itemsPerHour,
                xpUntilLevel,
                secondsUntilLevel,
                tracked.xpPerKill(),
                tracked.killsPerHour(),
                materialPriceProvider.isAvailable(),
                lePerHour,
                state);
    }

    private OptionalDouble calculateLePerHour(Map<MaterialKey, Double> rates) {
        return MaterialValueCalculator.liquidEmeraldsPerHour(
                rates, materialPriceProvider, materialPriceMode.get());
    }

    private OptionalDouble calculateCombatLePerHour(OptionalDouble itemsPerHour) {
        if (!materialPriceProvider.isAvailable()
                || trackedItemName.get().isBlank()
                || itemsPerHour.isEmpty()) {
            return OptionalDouble.empty();
        }

        OptionalDouble unitPrice =
                materialPriceProvider.getUnitPrice(trackedItemName.get(), materialPriceMode.get());
        if (unitPrice.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(itemsPerHour.getAsDouble() * unitPrice.getAsDouble()
                / EMERALDS_PER_LIQUID_EMERALD);
    }

    private List<CraftingComponentProjection> projectCraftingComponents(
            CraftingRecipe recipe, long crafts, boolean professionSpeedBombActive) {
        List<CraftingComponentProjection> projections = new ArrayList<>();
        for (CraftingComponent component : recipe.components()) {
            long amount = saturatingMultiply(crafts, component.amountPerCraft(professionSpeedBombActive));
            OptionalDouble cost = OptionalDouble.empty();
            if (materialPriceProvider.isAvailable()) {
                OptionalDouble unitPrice = component.kind() == CraftingComponentKind.MATERIAL
                        ? materialPriceProvider.getUnitPrice(
                                component.name(), component.tier(), materialPriceMode.get())
                        : materialPriceProvider.getUnitPrice(component.name(), materialPriceMode.get());
                if (unitPrice.isPresent()) {
                    double totalCost = amount * unitPrice.getAsDouble();
                    if (Double.isFinite(totalCost)) {
                        cost = OptionalDouble.of(totalCost);
                    }
                }
            }
            projections.add(new CraftingComponentProjection(component, amount, cost));
        }
        return List.copyOf(projections);
    }

    private void finishPendingCraft(long nowNanos) {
        PendingCraftXp pending = pendingCraftXp;
        if (pending == null) {
            return;
        }
        if (nowNanos < pending.occurredAtNanos()
                || nowNanos - pending.occurredAtNanos() > PENDING_CRAFT_TIMEOUT_NANOS) {
            pendingCraftXp = null;
            GatheringStatsClient.LOGGER.debug(
                    "Crafting XP was observed, but no consumed crafting-station recipe was available");
            return;
        }
        tryRecordCraft(pending, nowNanos);
    }

    private boolean tryRecordCraft(PendingCraftXp pending, long captureNowNanos) {
        AbstractContainerMenu menu = McUtils.player() == null ? null : McUtils.player().containerMenu;
        CraftingRecipe recipe = craftingRecipeCapture
                .completeCraft(pending.profession(), menu, captureNowNanos)
                .orElse(null);
        if (recipe == null) {
            return false;
        }
        craftingTracker.recordCraft(
                pending.profession(),
                pending.gainedXp(),
                pending.currentXpPercentage(),
                pending.currentLevel(),
                recipe,
                pending.bombState(),
                pending.occurredAtNanos());
        pendingCraftXp = null;
        return true;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
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
                    McUtils.sendWynntilsPrefixMessage(
                            Component.literal("Gathering, crafting, and combat statistics reset."));
                    return 1;
                }))
                .then(Commands.literal("track")
                        .executes(context -> sendTrackedItem())
                        .then(Commands.argument("itemName", StringArgumentType.greedyString())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(itemNames.knownNames(), builder))
                                .executes(context -> setTrackedItem(
                                        StringArgumentType.getString(context, "itemName")))))
                .then(Commands.literal("debug")
                        .executes(context -> sendDebugStatus())
                        .then(Commands.literal("status").executes(context -> sendDebugStatus()))
                        .then(Commands.literal("selftest").executes(context -> runDebugSelfTest()))
                        .then(Commands.literal("capture").executes(context -> setDebugCapture(true)))
                        .then(Commands.literal("capture-off").executes(context -> setDebugCapture(false)))
                        .then(Commands.literal("last").executes(context -> sendLastLabelDiagnostic())));
    }

    private int sendTrackedItem() {
        String selectedItem = trackedItemName.get();
        McUtils.sendWynntilsPrefixMessage(Component.literal(selectedItem.isBlank()
                ? "No combat-pickup item is selected. Use /gatherstats track <itemName>."
                : "Tracking combat pickups for: " + selectedItem));
        return selectedItem.isBlank() ? 0 : 1;
    }

    private int setTrackedItem(String requestedName) {
        String normalized = requestedName == null ? "" : requestedName.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            McUtils.sendErrorToClient("An item name is required.");
            return 0;
        }

        java.util.Optional<String> verifiedName = itemNames.canonicalName(normalized);
        String selectedName = verifiedName.orElse(normalized);
        trackedItemName.store(selectedName);
        combatTracker.reset();

        String verification = verifiedName.isPresent()
                ? " (verified by Wynntils)"
                : " (not in the currently loaded Wynntils registries; exact-name tracking enabled)";
        McUtils.sendWynntilsPrefixMessage(Component.literal("Now tracking: " + selectedName + verification));
        return 1;
    }

    private int sendDebugStatus() {
        TrackerSnapshot snapshot = tracker.snapshot();
        CraftingTrackerSnapshot craftingSnapshot = craftingTracker.snapshot();
        CombatTrackerSnapshot combatSnapshot = combatTracker.snapshot();
        debugMessage("capture=" + (debugCaptureEnabled ? "ON" : "OFF")
                + ", window=" + nodeWindowSize.get()
                + ", crafting window=" + craftingWindowSize.get()
                + ", inactivity=" + inactivityTimeoutMinutes.get() + "m"
                + ", WynnVentory=" + (materialPriceProvider.isAvailable() ? "available" : "not detected"));
        debugMessage("crafting: profession="
                + craftingSnapshot.profession().map(Enum::name).orElse("none")
                + ", XP samples=" + craftingSnapshot.xpSampleCount()
                + ", recipe=" + (craftingSnapshot.recipe().isPresent() ? "captured" : "none"));
        debugMessage("combat=" + onOff(enableCombatTracking.get())
                + ", tracked item=" + (trackedItemName.get().isBlank() ? "none" : trackedItemName.get())
                + ", XP samples=" + combatSnapshot.xpSampleCount()
                + ", kill samples=" + combatSnapshot.killSampleCount());
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
        craftingTracker.reset();
        combatTracker.reset();
        craftingRecipeCapture.reset();
        materialTierResolver.reset();
        pendingCraftXp = null;
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

    private static CraftingProfession craftingFromWynntils(ProfessionType profession) {
        return switch (profession) {
            case ALCHEMISM -> CraftingProfession.ALCHEMISM;
            case ARMOURING -> CraftingProfession.ARMOURING;
            case COOKING -> CraftingProfession.COOKING;
            case JEWELING -> CraftingProfession.JEWELING;
            case SCRIBING -> CraftingProfession.SCRIBING;
            case TAILORING -> CraftingProfession.TAILORING;
            case WEAPONSMITHING -> CraftingProfession.WEAPONSMITHING;
            case WOODWORKING -> CraftingProfession.WOODWORKING;
            default -> null;
        };
    }

    private static ProfessionType toWynntils(CraftingProfession profession) {
        return switch (profession) {
            case ALCHEMISM -> ProfessionType.ALCHEMISM;
            case ARMOURING -> ProfessionType.ARMOURING;
            case COOKING -> ProfessionType.COOKING;
            case JEWELING -> ProfessionType.JEWELING;
            case SCRIBING -> ProfessionType.SCRIBING;
            case TAILORING -> ProfessionType.TAILORING;
            case WEAPONSMITHING -> ProfessionType.WEAPONSMITHING;
            case WOODWORKING -> ProfessionType.WOODWORKING;
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

    private record PendingCraftXp(
            CraftingProfession profession,
            double gainedXp,
            double currentXpPercentage,
            int currentLevel,
            BombState bombState,
            long occurredAtNanos) {}

}
