package com.ssscript.taczfixes.client.render;

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
import org.joml.Vector4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* 在枪械模型的当前节点位姿上渲染第一人称手臂, 支持模型空间 Z 镜像。 */
public final class FirstPersonArmRenderHelper {
    private static final Matrix4f TACZFIXES$REFLECTION_X = new Matrix4f().scale(-1.0f, 1.0f, 1.0f);

    private FirstPersonArmRenderHelper() {
    }

    public static void render(BedrockAnimatedModel model, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, int light, HumanoidArm arm, boolean mirror, DualWieldOverrides.ArmOffset offset) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        if (mirror) {
            Matrix4f base = DualRenderContext.currentModelBase(model);
            if (base != null) {
                Matrix4f baseInverse = new Matrix4f(base).invert();
                Matrix4f reflection = new Matrix4f(base).mul(TACZFIXES$REFLECTION_X).mul(baseInverse);
                Vector4f origin = new Vector4f(pose.getTranslation(new Vector3f()), 1.0f);
                reflection.transform(origin);
                pose.setTranslation(origin.x(), origin.y(), origin.z());
            }
        }
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
            bufferSource.endBatch();
        });
    }
}
