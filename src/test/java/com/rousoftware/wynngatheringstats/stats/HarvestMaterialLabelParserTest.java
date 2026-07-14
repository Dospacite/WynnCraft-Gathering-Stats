package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HarvestMaterialLabelParserTest {
    @Test
    void parsesMaterialAmountFromTheUnformattedHarvestLine() {
        String label = "+125 Ⓖ Mining XP [42.1%]\n[x2] +4 Ingot Copper [✫✫✫]";

        assertEquals(4, HarvestMaterialLabelParser.parseAmount(label).orElseThrow());
    }

    @Test
    void parsesMaplePaperWithoutWynntilsMaterialRegistryData() {
        String label = "+125 Ⓖ Woodcutting XP [42.1%]\n[x2] +4 Maple Paper [★★★]";

        assertEquals(4, HarvestMaterialLabelParser.parseAmount(label).orElseThrow());
        assertEquals("Maple Paper", HarvestMaterialLabelParser.parseMaterialName(label).orElseThrow());
    }

    @Test
    void parsesCurrentMaplePaperLabelsThatDoNotContainTierStars() {
        String label = "+6196 Ⓗ Woodcutting XP [32.2%]\n+1 Maple Paper";

        HarvestLabelParseResult result = HarvestMaterialLabelParser.analyze(label, label);

        assertEquals(1, result.amount().orElseThrow());
        assertEquals("Maple Paper", result.materialName().orElseThrow());
        assertTrue(result.tier().isEmpty());
        assertTrue(result.hasAmountAndMaterial());
    }

    @Test
    void parsesEachActiveStarTierFromFormatting() {
        assertEquals(1, HarvestMaterialLabelParser.parseTier(stars("§e✫§8✫✫")).orElseThrow());
        assertEquals(2, HarvestMaterialLabelParser.parseTier(stars("§e✫✫§8✫")).orElseThrow());
        assertEquals(3, HarvestMaterialLabelParser.parseTier(stars("§e✫✫✫")).orElseThrow());
        assertEquals(2, HarvestMaterialLabelParser.parseTier(stars("§e★★§8★")).orElseThrow());
    }

    @Test
    void reportsExactlyWhichHarvestLabelValueIsMissing() {
        HarvestLabelParseResult result = HarvestMaterialLabelParser.analyze(
                "+125 Ⓖ Woodcutting XP [42.1%]\n+4 Maple Paper [✫✫✫]",
                "+125 Ⓖ Woodcutting XP [42.1%]\n+4 Maple Paper [✫✫✫]");

        assertFalse(result.isComplete());
        assertEquals("tier formatting was not found", result.failureReason());
    }

    @Test
    void rejectsLabelsWithoutACompleteMaterialLine() {
        assertTrue(HarvestMaterialLabelParser.parseAmount("+125 Mining XP").isEmpty());
        assertTrue(HarvestMaterialLabelParser.parseTier("[✫✫✫]").isEmpty());
    }

    private String stars(String stars) {
        return "§f+1 §7Ingot Copper§6 [" + stars + "§6]";
    }
}
