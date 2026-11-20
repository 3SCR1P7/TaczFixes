package com.ssscript.taczfixes.client.mixin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.attachment.AttachmentAnimationManager;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配件动画的功能组（camera / root / lefthand_pos / righthand_pos）。
 *
 * <p>在枪械模型渲染入口统一推进一次配件状态机，把这四个组的动画增量叠加到枪械模型的对应组上；
 * 渲染结束后还原。枪械模型是按 display 全局共享的，渲染完必须还原，否则会串到下一帧/别的玩家。</p>
 */
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class MixinBedrockGunModelGroupAnimation {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;"
                    + "IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = RENDER, at = @At("HEAD"), remap = false)
    private void taczfixes$beforeGunRender(PoseStack poseStack, ItemStack gunItem,
                                           ItemDisplayContext transformType, RenderType renderType,
                                           int light, int overlay, float red, float green, float blue,
                                           float alpha, MultiBufferSource bufferSource, CallbackInfo callback) {
        AttachmentAnimationManager.beforeGunRender((BedrockGunModel) (Object) this, gunItem, transformType);
    }

    @Inject(method = RENDER, at = @At("RETURN"), remap = false)
    private void taczfixes$afterGunRender(PoseStack poseStack, ItemStack gunItem,
                                          ItemDisplayContext transformType, RenderType renderType,
                                          int light, int overlay, float red, float green, float blue,
                                          float alpha, MultiBufferSource bufferSource, CallbackInfo callback) {
        AttachmentAnimationManager.afterGunRender();
    }
}
