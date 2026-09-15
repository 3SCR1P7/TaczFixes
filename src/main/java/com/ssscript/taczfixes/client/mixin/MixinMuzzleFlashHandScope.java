package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.ssscript.taczfixes.client.render.DualMuzzleHandScope;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {MuzzleFlashRender.class}, remap = false)
public abstract class MixinMuzzleFlashHandScope {
    @Inject(method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private void dualWield$cancelOtherHandMuzzleFlash(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay, MultiBufferSource.BufferSource bufferSource, CallbackInfo callback) {
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (phase != DualRenderContext.HandPhase.MAIN && phase != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        if (DualMuzzleHandScope.shouldCancel(phase)) {
            callback.cancel();
        }
    }
}
