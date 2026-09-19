package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.functional.RightHandRender;
import com.ssscript.taczfixes.client.render.DualReloadAnimationManager;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.FirstPersonArmRenderHelper;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RightHandRender.class}, remap = false)
public abstract class MixinRightHandRender {


    @Shadow
    @Final
    private BedrockAnimatedModel bedrockGunModel;

    @Inject(method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"}, at = {@At("HEAD")}, cancellable = true)
    private void taczfixes$renderConfiguredRightArm(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay, MultiBufferSource.BufferSource bufferSource, CallbackInfo callback) {
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (!transformType.firstPerson() || (phase != DualRenderContext.HandPhase.MAIN && phase != DualRenderContext.HandPhase.OFFHAND)) {
            return;
        }
        boolean offhand = phase == DualRenderContext.HandPhase.OFFHAND;
        if (DualReloadAnimationManager.areArmsHidden(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)) {
            callback.cancel();
            return;
        }
        DualWieldOverrides.HandPos handPos = offhand ? DualRenderContext.offhandHandPos() : DualRenderContext.mainHandPos();

        if (handPos.anchor() == DualWieldOverrides.ArmAnchor.UNSET) {
            return;
        }
        callback.cancel();
        boolean render = handPos.anchor() == DualWieldOverrides.ArmAnchor.RIGHT
                || handPos.anchor() == DualWieldOverrides.ArmAnchor.BOTH;
        if (!render || !this.bedrockGunModel.getRenderHand()) {
            return;
        }
        FirstPersonArmRenderHelper.render(this.bedrockGunModel, poseStack, bufferSource, light, HumanoidArm.RIGHT, handPos.mirror(), handPos.offset());
    }
}
