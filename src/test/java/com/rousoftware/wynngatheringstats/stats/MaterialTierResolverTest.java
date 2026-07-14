package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class MaterialTierResolverTest {
    @Test
    void joinsTierlessMaplePaperLabelWithItsMaterialItem() {
        MaterialTierResolver resolver = new MaterialTierResolver(Duration.ofSeconds(2));

        assertTrue(resolver.recordHarvestLabel("Maple Paper", 4, OptionalInt.empty(), 0).isEmpty());
        MaterialTierResolver.ResolvedMaterial resolved =
                resolver.recordMaterialItem("Maple Paper", 2, 1_000_000_000L).orElseThrow();

        assertEquals("Maple Paper", resolved.materialName());
        assertEquals(4, resolved.amount());
        assertEquals(2, resolved.tier());
    }

    @Test
    void joinsObservationsWhenTheMaterialPacketArrivesFirst() {
        MaterialTierResolver resolver = new MaterialTierResolver(Duration.ofSeconds(2));

        assertTrue(resolver.recordMaterialItem("Maple Paper", 3, 0).isEmpty());
        MaterialTierResolver.ResolvedMaterial resolved = resolver
                .recordHarvestLabel("Maple Paper", 1, OptionalInt.empty(), 1_000_000_000L)
                .orElseThrow();

        assertEquals(1, resolved.amount());
        assertEquals(3, resolved.tier());
    }

    @Test
    void doesNotJoinExpiredOrDifferentMaterials() {
        MaterialTierResolver resolver = new MaterialTierResolver(Duration.ofSeconds(2));

        assertTrue(resolver.recordHarvestLabel("Maple Paper", 1, OptionalInt.empty(), 0).isEmpty());
        assertTrue(resolver.recordMaterialItem("Birch Paper", 1, 1_000_000_000L).isEmpty());
        assertTrue(resolver.recordMaterialItem("Maple Paper", 2, 3_100_000_000L).isEmpty());
        assertEquals(1, resolver.pendingTierCount());
    }
}
