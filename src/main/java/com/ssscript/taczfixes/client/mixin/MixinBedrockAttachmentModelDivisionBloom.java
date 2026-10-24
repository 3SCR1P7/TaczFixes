package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelDivisionBloom {
    @Inject(method = "renderDivisionOnly", at = @At("HEAD"), remap = false)
    private void taczfixes$pushDivisionBloom(CallbackInfo callback) {
        PbrRenderer.pushScopeDivision();
    }

    @Inject(method = "renderDivisionOnly", at = @At("TAIL"), remap = false)
    private void taczfixes$popDivisionBloom(CallbackInfo callback) {
        PbrRenderer.popScopeDivision();
    }

    @Inject(method = "captureLensDivision", at = @At("HEAD"), remap = false)
    private void taczfixes$pushLensDivisionBloom(CallbackInfo callback) {
        PbrRenderer.pushScopeDivision();
    }

    @Inject(method = "captureLensDivision", at = @At("TAIL"), remap = false)
    private void taczfixes$popLensDivisionBloom(CallbackInfo callback) {
        PbrRenderer.popScopeDivision();
    }
}
