package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.render.DualThirdPersonRenderer;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ItemInHandLayer.class}, priority = 1100)
public abstract class MixinDualItemInHandLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {
    protected MixinDualItemInHandLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Inject(method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/ItemInHandLayer;renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", ordinal = ServerMessageOffhandActionResult.ACTION_RELOAD, shift = At.Shift.BEFORE)}, require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private void dualWield$renderOffhandBeforeTaczCancellation(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (!DualThirdPersonRenderer.isDualMode(entity)) {
            return;
        }
        renderAtHand(entity, entity.getOffhandItem(), HumanoidArm.LEFT, poseStack, buffer, packedLight, true);
    }

    @Inject(method = {"renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At("HEAD")}, cancellable = true)
    private void dualWield$renderPhysicalHands(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callback) {
        if (!DualThirdPersonRenderer.isDualMode(entity)) {
            return;
        }
        if (arm == HumanoidArm.LEFT) {
            callback.cancel();
        } else {
            renderAtHand(entity, entity.getMainHandItem(), HumanoidArm.RIGHT, poseStack, buffer, packedLight, false);
            callback.cancel();
        }
    }

    private void renderAtHand(LivingEntity entity, ItemStack stack, HumanoidArm physicalArm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean offhand) {
        poseStack.pushPose();
        try {
            getParentModel().translateToHand(physicalArm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            float side = physicalArm == HumanoidArm.LEFT ? -1.0f : 1.0f;
            poseStack.translate(side / 16.0f, 0.125f, -0.625f);
            if (offhand) {
                DualThirdPersonRenderer.renderOffhand(entity, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            } else {
                DualThirdPersonRenderer.renderMainhand(entity, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
