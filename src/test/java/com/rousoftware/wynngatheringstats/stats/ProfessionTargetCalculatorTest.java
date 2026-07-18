package com.rousoftware.wynngatheringstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProfessionTargetCalculatorTest {
    @Test
    void choosesTheNextTenthLevelAndRespectsTheProfessionCap() {
        assertEquals(80, ProfessionTargetCalculator.nextTenthLevel(73, 132));
        assertEquals(90, ProfessionTargetCalculator.nextTenthLevel(80, 132));
        assertEquals(130, ProfessionTargetCalculator.nextTenthLevel(128, 132));
        assertEquals(132, ProfessionTargetCalculator.nextTenthLevel(130, 132));
        assertEquals(132, ProfessionTargetCalculator.nextTenthLevel(132, 132));
    }

    @Test
    void sumsCurrentLevelRemainderAndEveryFullLevelUntilTarget() {
        int[] requirements = new int[131];
        Arrays.fill(requirements, 100);

        // Level 73 -> 80 includes the current remainder plus levels 74 through 79.
        assertEquals(
                650,
                ProfessionTargetCalculator.xpUntilTarget(73, 80, 50, requirements)
                        .orElseThrow());
    }

    @Test
    void rejectsUnsynchronizedOrIncompleteLevelData() {
        assertTrue(ProfessionTargetCalculator.xpUntilTarget(0, 10, 10, new int[20]).isEmpty());
        assertTrue(ProfessionTargetCalculator.xpUntilTarget(73, 80, 10, new int[75]).isEmpty());
    }
}
