package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.util.RenderHelper;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* 在枪械模型的当前节点位姿上渲染第一人称手臂。整枪镜像由模型基准矩阵承担(见 DualFirstPersonRenderer / MixinFirstPersonRenderGunEvent), 手臂随镜像模型一起变换。 */
public final class FirstPersonArmRenderHelper {

    private FirstPersonArmRenderHelper() {
    }

    public static void render(BedrockAnimatedModel model, PoseStack poseStack, MultiBufferSource bufferSource, int light, HumanoidArm arm, DualWieldOverrides.ArmOffset offset) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        if (offset != null && !offset.isZero()) {
            Vector3f worldOffset = new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z());
            Matrix4f base = DualRenderContext.currentModelBase(model);
            if (base != null) {
                worldOffset = new Matrix3f(base).transform(worldOffset);
            }
            pose = new Matrix4f().translation(worldOffset).mul(pose);
        }
        Matrix3f normal = new Matrix3f(poseStack.last().normal());
        Matrix3f candidate = new Matrix3f(pose);
        float determinant = candidate.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) >= 1.0E-8f) {
            candidate.invert().transpose();
            normal = candidate;
        }
        Matrix4f finalPose = pose;
        Matrix3f finalNormal = normal;
        model.delegateRender((deferredPose, deferredBuffer, deferredType, deferredLight, deferredOverlay) -> {
            PoseStack armPose = new PoseStack();
            armPose.last().normal().set(finalNormal);
            armPose.last().pose().set(finalPose);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            RenderHelper.renderFirstPersonArm(player, arm, armPose, deferredLight, bufferSource);
            if (bufferSource instanceof MultiBufferSource.BufferSource batch) batch.endBatch();
        });
    }
}
