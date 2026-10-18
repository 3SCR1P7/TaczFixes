package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GunRotationInertiaTransform {
    private static float lagYaw;
    private static float lagPitch;
    private static float lastYaw;
    private static float lastPitch;
    private static long lastNanos;
    private static boolean initialized;

    private GunRotationInertiaTransform() {
    }

    public static boolean apply(PoseStack pose, com.tacz.guns.client.model.BedrockGunModel model, ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        float maxAngle = Config.ROTATION_INERTIA_MAX_ANGLE.get().floatValue();
        if (maxAngle <= 0f) {
            return false;
        }
        float partial = Minecraft.getInstance().getFrameTime();
        float yaw = player.getViewYRot(partial);
        float pitch = player.getViewXRot(partial);
        if (!initialized) {
            initialized = true;
            lastYaw = yaw;
            lastPitch = pitch;
        }
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
        }
        float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
        lastNanos = now;
        if (dt <= 0f) {
            return false;
        }
        float yawVelocity = Mth.clamp(Mth.wrapDegrees(yaw - lastYaw) / dt, -1440f, 1440f);
        float pitchVelocity = Mth.clamp((pitch - lastPitch) / dt, -1440f, 1440f);
        lastYaw = yaw;
        lastPitch = pitch;

        float speed = Math.max(0.1f, Config.ROTATION_INERTIA_FOLLOW_SPEED.get().floatValue());
        float alpha = 1.0f - (float) Math.exp(-speed * dt);
        float targetYaw = Mth.clamp(-yawVelocity * 0.05f, -maxAngle, maxAngle);
        float targetPitch = Mth.clamp(-pitchVelocity * 0.04f, -maxAngle, maxAngle);
        lagYaw += (targetYaw - lagYaw) * alpha;
        lagPitch += (targetPitch - lagPitch) * alpha;

        float aimMultiplier = Config.ROTATION_INERTIA_AIM_MULTIPLIER.get().floatValue();
        float aimFactor = Mth.lerp(aimProgress(player), 1.0f, aimMultiplier);
        float appliedYaw = lagYaw * aimFactor;
        float appliedPitch = lagPitch * aimFactor;
        if (Math.abs(appliedYaw) < 0.01f && Math.abs(appliedPitch) < 0.01f) {
            return false;
        }
        Matrix4f base = new Matrix4f(pose.last().pose());
        Vector3f pivot = new Vector3f();
        float[] center = GunModelPivot.centerStable(model, stack);
        if (center != null) {
            base.transformPosition(center[0], center[1], center[2], pivot);
        }
        Matrix4f screen = new Matrix4f()
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateY((float) Math.toRadians(appliedYaw))
                .rotateX((float) Math.toRadians(appliedPitch))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        Matrix4f local = new Matrix4f(base).invert().mul(screen).mul(base);
        pose.pushPose();
        pose.mulPoseMatrix(local);
        return true;
    }

    private static float aimProgress(LocalPlayer player) {
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, operator.getClientAimingProgress(
                Minecraft.getInstance().getFrameTime())));
    }
}
