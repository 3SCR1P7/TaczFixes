package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
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
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
/* 在枪械模型的手部节点上渲染第一人称手臂。
 * mirror=true 时沿枪械模型自身的 yOz 平面把手臂反射到另一侧, 枪械模型本身不镜像。
 * 镜面使用模型根骨骼的当前动画变换, 因此枪械播放动画时手臂的运动方向与未镜像时一致。 */
public final class FirstPersonArmRenderHelper {

    private FirstPersonArmRenderHelper() {
    }

    public static void render(BedrockAnimatedModel model, PoseStack poseStack, MultiBufferSource bufferSource, int light,
                              HumanoidArm arm, DualWieldOverrides.ArmOffset offset, boolean mirror, boolean swapArmFrame) {
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
        if (mirror) {
            Matrix4f base = DualRenderContext.currentModelBase(model);
            if (base != null) {
                Matrix4f root = rootBoneMatrix(model);
                Matrix4f mirrorBase = root == null ? base : new Matrix4f(base).mul(root);
                Matrix4f mirrorBaseInverse = new Matrix4f(mirrorBase).invert();
                pose = new Matrix4f(mirrorBase)
                        .mul(new Matrix4f().scaling(-1.0f, 1.0f, 1.0f))
                        .mul(mirrorBaseInverse)
                        .mul(pose);
            }
        }
        if (swapArmFrame) {
            pose = new Matrix4f(pose).scale(-1.0f, 1.0f, 1.0f);
        }
        Matrix3f normal = new Matrix3f(poseStack.last().normal());
        Matrix3f candidate = new Matrix3f(pose);
        float determinant = candidate.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) >= 1.0E-8f) {
            candidate.invert().transpose();
            normal = candidate;
        }
        boolean reflected = pose.determinant() < 0.0f;
        Matrix4f finalPose = pose;
        Matrix3f finalNormal = normal;
        model.delegateRender((deferredPose, deferredBuffer, deferredType, deferredLight, deferredOverlay) -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            PoseStack armPose = new PoseStack();
            armPose.last().normal().set(finalNormal);
            armPose.last().pose().set(finalPose);
            if (reflected) {
                if (bufferSource instanceof MultiBufferSource.BufferSource batch) {
                    batch.endBatch();
                }
                boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                RenderSystem.enableCull();
                GL11.glFrontFace(GL11.GL_CW);
                try {
                    RenderHelper.renderFirstPersonArm(player, arm, armPose, deferredLight, bufferSource);
                    if (bufferSource instanceof MultiBufferSource.BufferSource batch) {
                        batch.endBatch();
                    }
                } finally {
                    GL11.glFrontFace(GL11.GL_CCW);
                    if (cullEnabled) {
                        RenderSystem.enableCull();
                    } else {
                        RenderSystem.disableCull();
                    }
                }
            } else {
                RenderHelper.renderFirstPersonArm(player, arm, armPose, deferredLight, bufferSource);
                if (bufferSource instanceof MultiBufferSource.BufferSource batch) {
                    batch.endBatch();
                }
            }
        });
    }

    /** 模型根骨骼当前的世界变换(顺序与 BedrockPart#translateAndRotateAndScale 一致)。 */
    private static Matrix4f rootBoneMatrix(BedrockAnimatedModel model) {
        BedrockPart root = model == null ? null : model.getRootNode();
        if (root == null) {
            return null;
        }
        Matrix4f matrix = new Matrix4f();
        applyPartMatrix(matrix, root);
        return matrix;
    }

    private static void applyPartMatrix(Matrix4f matrix, BedrockPart part) {
        matrix.translate(part.offsetX, part.offsetY, part.offsetZ);
        matrix.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f);
        if (part.zRot != 0.0f) {
            matrix.rotateZ(part.zRot);
        }
        if (part.yRot != 0.0f) {
            matrix.rotateY(part.yRot);
        }
        if (part.xRot != 0.0f) {
            matrix.rotateX(part.xRot);
        }
        matrix.rotate(part.additionalQuaternion);
        matrix.scale(part.xScale, part.yScale, part.zScale);
    }
}
