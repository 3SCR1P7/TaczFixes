package com.ssscript.taczfixes.client.mixin.pbr;

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

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczfixes$pbrComposite(float tick, long time, PoseStack pose, CallbackInfo ci) {
        PbrBloom.endFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void taczfixes$pbrEnd(float tick, long time, boolean renderLevel, CallbackInfo ci) {
        com.ssscript.taczfixes.client.render.AimBlur.composite();
        com.ssscript.taczfixes.client.render.SuppressionEffect.composite();
    }
}
