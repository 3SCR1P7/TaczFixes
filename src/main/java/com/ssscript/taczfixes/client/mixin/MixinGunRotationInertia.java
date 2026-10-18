package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.client.util.GunRotationInertiaTransform;
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

/** 第一人称主手枪械的视角旋转惯性。 */
@Mixin(BedrockGunModel.class)
public abstract class MixinGunRotationInertia {
    @Unique
    private boolean taczfixes$rotationInertiaPushed;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$rotationInertiaHead(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                               RenderType type, int light, int overlay, float red, float green,
                                               float blue, float alpha, MultiBufferSource buffers, CallbackInfo ci) {
        taczfixes$rotationInertiaPushed = false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        taczfixes$rotationInertiaPushed = GunRotationInertiaTransform.apply(
                pose, (BedrockGunModel) (Object) this, stack);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$rotationInertiaTail(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                               RenderType type, int light, int overlay, float red, float green,
                                               float blue, float alpha, MultiBufferSource buffers, CallbackInfo ci) {
        if (taczfixes$rotationInertiaPushed) {
            pose.popPose();
            taczfixes$rotationInertiaPushed = false;
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$rotationInertiaHeadFp(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                                 RenderType type, int light, int overlay, CallbackInfo ci) {
        taczfixes$rotationInertiaPushed = false;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        taczfixes$rotationInertiaPushed = GunRotationInertiaTransform.apply(
                pose, (BedrockGunModel) (Object) this, stack);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$rotationInertiaTailFp(PoseStack pose, ItemStack stack, ItemDisplayContext context,
                                                 RenderType type, int light, int overlay, CallbackInfo ci) {
        if (taczfixes$rotationInertiaPushed) {
            pose.popPose();
            taczfixes$rotationInertiaPushed = false;
        }
    }
}
