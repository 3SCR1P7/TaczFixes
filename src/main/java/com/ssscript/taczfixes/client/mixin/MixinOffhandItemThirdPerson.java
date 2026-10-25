package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class MixinOffhandItemThirdPerson<T extends LivingEntity, M extends EntityModel<T> & ArmedModel>
        extends RenderLayer<T, M> {

    protected MixinOffhandItemThirdPerson(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("TAIL"))
    private void taczfixes$renderOffhandItem(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float partialTicks, float ageInTicks, float netHeadYaw,
                                              float headPitch, CallbackInfo ci) {
        ItemStack offhand = entity.getOffhandItem();
        if (offhand.isEmpty() || IGun.getIGunOrNull(offhand) != null || !IGun.mainHandHoldGun(entity)
                || entity.getMainArm() != HumanoidArm.RIGHT) {
            return;
        }
        poseStack.pushPose();
        try {
            this.getParentModel().translateToHand(HumanoidArm.LEFT, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate(-1.0F / 16.0F, 0.125F, -0.625F);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(entity, offhand,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
