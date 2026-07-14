package com.rousoftware.wynngatheringstats.stats;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Joins a tierless gathering label with the MaterialItem packet that carries its quality tier.
 * Wynncraft no longer includes a material's tier in the floating harvest label.
 */
public final class MaterialTierResolver {
    public static final Duration DEFAULT_MATCH_WINDOW = Duration.ofSeconds(2);
    private static final int MAX_PENDING_OBSERVATIONS = 16;

    private final long matchWindowNanos;
    private final Deque<PendingHarvest> pendingHarvests = new ArrayDeque<>();
    private final Deque<MaterialTier> pendingTiers = new ArrayDeque<>();

    public MaterialTierResolver() {
        this(DEFAULT_MATCH_WINDOW);
    }

    MaterialTierResolver(Duration matchWindow) {
        if (matchWindow.isNegative() || matchWindow.isZero()) {
            throw new IllegalArgumentException("match window must be positive");
        }
        matchWindowNanos = matchWindow.toNanos();
    }

    public synchronized Optional<ResolvedMaterial> recordHarvestLabel(
            String materialName, int amount, OptionalInt labelTier, long nowNanos) {
        if (!isUsableName(materialName) || amount <= 0) {
            return Optional.empty();
        }
        removeExpired(nowNanos);

        if (labelTier.isPresent()) {
            return resolve(materialName, amount, labelTier.getAsInt());
        }

        MaterialTier detectedTier = removeMatchingTier(materialName);
        if (detectedTier != null) {
            return resolve(detectedTier.materialName(), amount, detectedTier.tier());
        }

        addBounded(pendingHarvests, new PendingHarvest(materialName, amount, nowNanos));
        return Optional.empty();
    }

    public synchronized Optional<ResolvedMaterial> recordMaterialItem(String materialName, int tier, long nowNanos) {
        if (!isUsableName(materialName) || tier < 1 || tier > 3) {
            return Optional.empty();
        }
        removeExpired(nowNanos);

        PendingHarvest pendingHarvest = removeMatchingHarvest(materialName);
        if (pendingHarvest != null) {
            return resolve(materialName, pendingHarvest.amount(), tier);
        }

        addBounded(pendingTiers, new MaterialTier(materialName, tier, nowNanos));
        return Optional.empty();
    }

    public synchronized void reset() {
        pendingHarvests.clear();
        pendingTiers.clear();
    }

    public synchronized int pendingHarvestCount() {
        return pendingHarvests.size();
    }

    public synchronized int pendingTierCount() {
        return pendingTiers.size();
    }

    private Optional<ResolvedMaterial> resolve(String materialName, int amount, int tier) {
        return tier >= 1 && tier <= 3
                ? Optional.of(new ResolvedMaterial(materialName, amount, tier))
                : Optional.empty();
    }

    private MaterialTier removeMatchingTier(String materialName) {
        return removeFirstMatching(pendingTiers, tier -> namesMatch(tier.materialName(), materialName));
    }

    private PendingHarvest removeMatchingHarvest(String materialName) {
        return removeFirstMatching(pendingHarvests, harvest -> namesMatch(harvest.materialName(), materialName));
    }

    private <T> T removeFirstMatching(Deque<T> values, java.util.function.Predicate<T> predicate) {
        for (java.util.Iterator<T> iterator = values.iterator(); iterator.hasNext(); ) {
            T value = iterator.next();
            if (predicate.test(value)) {
                iterator.remove();
                return value;
            }
        }
        return null;
    }

    private void removeExpired(long nowNanos) {
        pendingHarvests.removeIf(harvest -> isExpired(harvest.observedAtNanos(), nowNanos));
        pendingTiers.removeIf(tier -> isExpired(tier.observedAtNanos(), nowNanos));
    }

    private boolean isExpired(long observedAtNanos, long nowNanos) {
        return nowNanos < observedAtNanos || nowNanos - observedAtNanos > matchWindowNanos;
    }

    private static <T> void addBounded(Deque<T> values, T value) {
        values.addLast(value);
        if (values.size() > MAX_PENDING_OBSERVATIONS) {
            values.removeFirst();
        }
    }

    private static boolean isUsableName(String name) {
        return name != null && !name.isBlank();
    }

    private static boolean namesMatch(String first, String second) {
        return first.equalsIgnoreCase(second);
    }

    public record ResolvedMaterial(String materialName, int amount, int tier) {}

    private record PendingHarvest(String materialName, int amount, long observedAtNanos) {}

    private record MaterialTier(String materialName, int tier, long observedAtNanos) {}
}
