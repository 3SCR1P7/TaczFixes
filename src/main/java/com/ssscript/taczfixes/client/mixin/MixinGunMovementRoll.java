package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.client.util.MovementRollTransform;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockGunModel.class)
public abstract class MixinGunMovementRoll {
    @Unique
    private boolean taczfixes$movementRollPushed;
    @Unique
    private int taczfixes$movementRollDepth;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$movementRollHead(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                            RenderType type, int light, int overlay, float red, float green,
                                            float blue, float alpha, MultiBufferSource buffers, CallbackInfo ci) {
        if (taczfixes$movementRollDepth++ != 0) {
            return;
        }
        taczfixes$movementRollPushed = false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        taczfixes$movementRollPushed = MovementRollTransform.apply(pose, (BedrockGunModel) (Object) this, stack);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$movementRollTail(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                            RenderType type, int light, int overlay, float red, float green,
                                            float blue, float alpha, MultiBufferSource buffers, CallbackInfo ci) {
        if (--taczfixes$movementRollDepth == 0 && taczfixes$movementRollPushed) {
            pose.popPose();
            taczfixes$movementRollPushed = false;
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$movementRollHeadFp(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                              RenderType type, int light, int overlay, CallbackInfo ci) {
        if (taczfixes$movementRollDepth++ != 0) {
            return;
        }
        taczfixes$movementRollPushed = false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        taczfixes$movementRollPushed = MovementRollTransform.apply(pose, (BedrockGunModel) (Object) this, stack);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$movementRollTailFp(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                              RenderType type, int light, int overlay, CallbackInfo ci) {
        if (--taczfixes$movementRollDepth == 0 && taczfixes$movementRollPushed) {
            pose.popPose();
            taczfixes$movementRollPushed = false;
        }
    }
}
