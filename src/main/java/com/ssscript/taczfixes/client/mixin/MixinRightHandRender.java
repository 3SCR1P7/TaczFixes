package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.functional.RightHandRender;
import com.tacz.guns.util.RenderHelper;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.OffhandArmPoseResolver;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {RightHandRender.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinRightHandRender.class */
public abstract class MixinRightHandRender {

    @Shadow
    @Final
    private BedrockAnimatedModel bedrockGunModel;

    @Inject(method = {"render"}, at = {@At("HEAD")}, cancellable = true)
    private void dualWield$renderAsLeftArm(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay, CallbackInfo callback) {
        if (DualRenderContext.getPhase() != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        callback.cancel();
        if (!transformType.firstPerson() || !this.bedrockGunModel.getRenderHand() || OffhandDisplayManager.shouldUseInwardSupportArmForManualAction(this.bedrockGunModel)) {
            return;
        }
        Matrix4f rightArmPose = new Matrix4f(poseStack.last().pose());
        Matrix4f pose = DualRenderContext.resolveOffhandArmPose(this.bedrockGunModel, rightArmPose);
        boolean inwardBoltAction = OffhandDisplayManager.shouldUseInwardHoldingArmForManualAction(this.bedrockGunModel) && OffhandArmPoseResolver.hasBoltNamedBone(this.bedrockGunModel);
        Matrix3f normal = new Matrix3f(pose);
        float normalDeterminant = normal.determinant();
        if (Float.isFinite(normalDeterminant) && Math.abs(normalDeterminant) >= 1.0E-8f) {
            normal.invert().transpose();
        } else {
            normal.set(poseStack.last().normal());
        }
        this.bedrockGunModel.delegateRender((deferredPose, deferredBuffer, deferredType, deferredLight, deferredOverlay) -> {
            PoseStack armPose = new PoseStack();
            armPose.last().normal().set(normal);
            armPose.last().pose().set(pose);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            if (inwardBoltAction) {
                armPose.translate(OffhandArmPoseResolver.getInwardLeftArmProxyOffsetX("slim".equals(player.getModelName())), 0.0d, 0.0d);
            }
            RenderHelper.renderFirstPersonArm(player, HumanoidArm.RIGHT, armPose, deferredLight);
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        });
    }
}
