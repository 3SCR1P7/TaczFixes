package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.AimingBlurConfig;
import com.ssscript.taczfixes.common.util.AimingBlurDisplayAccessor;
import com.ssscript.taczfixes.common.util.SteplessConfig;
import com.ssscript.taczfixes.common.util.SteplessDisplayAccessor;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AttachmentDisplay.class, remap = false)
public abstract class MixinAttachmentDisplayStepless implements SteplessDisplayAccessor, AimingBlurDisplayAccessor {
    @Unique
    private SteplessConfig stepless;

    /** JSON 键名即字段名, 由 Gson 反序列化直接填充。 */
    @Unique
    private AimingBlurConfig aiming_blur;

    @Override
    public SteplessConfig getStepless() {
        return stepless;
    }

    @Override
    public void setStepless(SteplessConfig stepless) {
        this.stepless = stepless;
    }

    @Override
    public AimingBlurConfig getAimingBlur() {
        return aiming_blur;
    }

    @Override
    public void setAimingBlur(AimingBlurConfig aimingBlur) {
        this.aiming_blur = aimingBlur;
    }
}
