package com.ssscript.taczfixes.client.mixin.shader;

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
        // 压制效果必须挪到 renderLevel 结束(而不是 render 结束): 此时 HUD/GUI 尚未绘制, 只作用于世界与手部;
        // 放在 endFrame() 之后, 模糊/色散/暗角才会叠加到泛光结果上。
        com.ssscript.taczfixes.client.render.SuppressionEffect.composite();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", shift = At.Shift.BEFORE))
    private void taczfixes$aimDepthOfField(float tick, long time, PoseStack pose, CallbackInfo ci) {
        com.ssscript.taczfixes.client.render.AimBlur.composite();
    }
}
