package com.ssscript.taczfixes.client.mixin.shader;

import com.ssscript.taczfixes.client.render.PbrBloom;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRendererPbr {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void taczfixes$pbrBegin(DeltaTracker deltaTracker, CallbackInfo ci) {
        PbrRenderer.updateCelestialLight(deltaTracker.getGameTimeDeltaPartialTick(false));
        PbrBloom.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczfixes$pbrComposite(DeltaTracker deltaTracker, CallbackInfo ci) {
        PbrBloom.endFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void taczfixes$pbrEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        com.ssscript.taczfixes.client.render.AimBlur.composite();
        com.ssscript.taczfixes.client.render.SuppressionEffect.composite();
    }
}
