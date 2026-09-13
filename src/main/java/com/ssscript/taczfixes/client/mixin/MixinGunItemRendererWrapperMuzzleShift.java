package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.CustomScopeViewShift;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义槽瞄具开镜偏移的弹出点。
 * 偏移在 FirstPersonRenderGunEvent.applyFirstPersonGunTransform 返回时压入,
 * renderFirstPerson 的渲染序列为: 定位 -> spawnAndBind(枪口粒子) -> model.render -> cacheMuzzlePosition,
 * 因此在 cacheMuzzlePosition 返回时弹出, 保证火光/粒子与偏移后的枪身一致。
 */
@Mixin(GunItemRendererWrapper.class)
public abstract class MixinGunItemRendererWrapperMuzzleShift {

    @Inject(method = "cacheMuzzlePosition", at = @At("RETURN"), remap = false)
    private static void taczfixes$popScopeViewShift(PoseStack poseStack, BedrockGunModel model, CallbackInfo ci) {
        CustomScopeViewShift.pop(poseStack);
    }
}
