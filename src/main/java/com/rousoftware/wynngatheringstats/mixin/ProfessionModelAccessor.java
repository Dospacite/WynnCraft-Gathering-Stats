package com.rousoftware.wynngatheringstats.mixin;

import com.wynntils.models.profession.ProfessionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ProfessionModel.class, remap = false)
public interface ProfessionModelAccessor {
    @Accessor("LEVEL_UP_XP_REQUIREMENTS")
    static int[] gatheringStats$getLevelUpXpRequirements() {
        throw new AssertionError();
    }
}
