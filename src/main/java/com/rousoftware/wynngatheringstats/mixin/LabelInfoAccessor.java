package com.rousoftware.wynngatheringstats.mixin;

import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.labels.type.LabelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LabelInfo.class)
public interface LabelInfoAccessor {
    @Accessor("label")
    StyledText gatheringStats$getLabel();
}
