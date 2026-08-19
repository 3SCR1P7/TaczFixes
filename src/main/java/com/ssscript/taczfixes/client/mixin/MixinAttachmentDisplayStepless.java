package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.SteplessConfig;
import com.ssscript.taczfixes.common.util.SteplessDisplayAccessor;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AttachmentDisplay.class, remap = false)
public abstract class MixinAttachmentDisplayStepless implements SteplessDisplayAccessor {
    @Unique
    private SteplessConfig stepless;

    @Override
    public SteplessConfig getStepless() {
        return stepless;
    }

    @Override
    public void setStepless(SteplessConfig stepless) {
        this.stepless = stepless;
    }
}
