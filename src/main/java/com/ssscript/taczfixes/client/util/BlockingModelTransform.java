package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.GunBlocking;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * blocking 模型表现: 非双持向左旋转, 双持向上旋转, 均以 positioning/ground 组为中心并向后退。
 * 旋转与后退都在世界(视线)空间定义, 再映射到当前姿态空间, 因此与手部姿态/镜像无关; 强度做平滑过渡。
 */
public final class BlockingModelTransform {
    private static final float SMOOTH_TAU = 0.12f;
    private static float mainFactor;
    private static float offhandFactor;
    private static long mainNanos;
    private static long offhandNanos;

    private BlockingModelTransform() {
    }

    /** 应用阻挡变换; 返回 true 表示已 pushPose, 调用方需负责 popPose。 */
    public static boolean apply(PoseStack pose, BedrockGunModel model, ItemStack gunStack,
                                LocalPlayer player, boolean forceDual) {
        if (pose == null || model == null || gunStack == null || player == null) {
            return false;
        }
        GunTaczFixesData.BlockingConfig cfg = GunBlocking.resolve(gunStack);
        double target = GunBlocking.isEnabled(gunStack)
                ? GunBlocking.factor(player, GunBlocking.distanceMax(cfg), GunBlocking.distanceMin(cfg))
                : 0.0d;
        float factor = smooth(forceDual, (float) target, System.nanoTime());
        if (factor <= 0.001f) {
            return false;
        }
        float[] center = GunModelPivot.center(model, gunStack);
        if (center == null) {
            return false;
        }
        double angle = Math.toRadians(GunBlocking.angleDeg(cfg) * factor);
        double back = GunBlocking.backOff(cfg) * factor;
        boolean dual = forceDual || DualWieldEligibility.isDualWielding(player);
        // 视线空间(相机在原点, X右/Y上/-Z前): 偏转方向 facing(0=右,90=上,180=左,-90=下), 绕 前方×方向 轴旋转
        double facingRad = Math.toRadians(dual ? GunBlocking.facingDual(cfg) : GunBlocking.facing(cfg));
        Vector3f axis = new Vector3f((float) Math.sin(facingRad), (float) -Math.cos(facingRad), 0.0f);
        if (axis.lengthSquared() < 1.0E-8f) {
            return false;
        }
        Quaternionf rotation = new Quaternionf().rotationAxis((float) angle, axis.normalize());
        Vector3f backVector = new Vector3f(0.0f, 0.0f, (float) back);
        // 精确屏幕空间变换: 最终等价于在屏幕空间绕模型枢轴旋转并沿视线后退, 与姿态缩放/手部变换无关
        Matrix4f base = new Matrix4f(pose.last().pose());
        Vector3f pivot = base.transformPosition(center[0], center[1], center[2], new Vector3f());
        Matrix4f screen = new Matrix4f()
                .translation(backVector)
                .translate(pivot.x, pivot.y, pivot.z)
                .rotate(rotation)
                .translate(-pivot.x, -pivot.y, -pivot.z);
        Matrix4f local = new Matrix4f(base).invert().mul(screen).mul(base);
        pose.pushPose();
        pose.mulPoseMatrix(local);
        return true;
    }

    private static float smooth(boolean offhand, float target, long now) {
        float current = offhand ? offhandFactor : mainFactor;
        long last = offhand ? offhandNanos : mainNanos;
        float dt = last == 0L ? 1.0f / 60.0f : Math.min((now - last) / 1_000_000_000.0f, 0.1f);
        float decay = (float) Math.exp(-dt / SMOOTH_TAU);
        float next = target + (current - target) * decay;
        if (Math.abs(target - next) < 0.001f) {
            next = target;
        }
        if (offhand) {
            offhandFactor = next;
            offhandNanos = now;
        } else {
            mainFactor = next;
            mainNanos = now;
        }
        return next;
    }
}
