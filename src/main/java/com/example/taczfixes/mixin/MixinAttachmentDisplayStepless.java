package com.example.taczfixes.mixin;

import com.example.taczfixes.util.SteplessConfig;
import com.example.taczfixes.util.SteplessDisplayAccessor;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 为瞄具配件的 AttachmentDisplay 增加 stepless 字段。
 * TACZ 使用标准 Gson 按字段名反序列化 display 文件，因此该字段
 * 会被 JSON 中的 "stepless" 对象自动填充（缺失时保持默认 null）。
 */
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
