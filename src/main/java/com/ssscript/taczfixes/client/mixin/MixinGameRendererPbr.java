package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.PbrBloom;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererPbr {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void taczfixes$pbrBegin(float tick, long time, PoseStack pose, CallbackInfo ci) {
        PbrRenderer.updateCelestialLight(tick);
        PbrBloom.beginFrame();
    }

    // World and hands use different depth/projection spaces. Composite before the hand depth clear.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", remap = false))
    private void taczfixes$pbrWorld(float tick, long time, PoseStack pose, CallbackInfo ci) {
        PbrBloom.composite();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczfixes$pbrEnd(float tick, long time, PoseStack pose, CallbackInfo ci) {
        PbrBloom.endFrame();
    }
}
