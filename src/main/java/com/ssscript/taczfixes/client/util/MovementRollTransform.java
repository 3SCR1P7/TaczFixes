package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class MovementRollTransform {
    private static float smoothedAngle;
    private static long lastNanos;

    private MovementRollTransform() {
    }

    public static boolean apply(PoseStack pose, BedrockGunModel model, ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }
        float maxAngle = Config.MOVEMENT_ROLL_MAX_ANGLE.get().floatValue();
        float fullSpeed = Config.MOVEMENT_ROLL_FULL_SPEED.get().floatValue();
        boolean dual = DualWieldClient.isDualMode(player);
        float aimProgress = aimProgress(player);
        float target = 0f;
        if (!dual && maxAngle > 0f && fullSpeed > 0f) {
            Vec3 view = player.getViewVector(1f);
            Vec3 right = new Vec3(-view.z, 0.0d, view.x);
            if (right.lengthSqr() > 1.0E-8d) {
                right = right.normalize();
                double lateral = player.getDeltaMovement().dot(right) * 20.0d;
                target = (float) (-lateral / fullSpeed * maxAngle);
                target = Math.max(-maxAngle, Math.min(maxAngle, target));
            }
        }
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
        }
        float dt = Math.min((now - lastNanos) / 1.0e9f, 0.1f);
        lastNanos = now;
        float alpha = 1.0f - (float) Math.exp(-12.0f * dt);
        smoothedAngle += (target - smoothedAngle) * alpha;
        if (Math.abs(smoothedAngle) < 0.01f) {
            smoothedAngle = 0f;
            return false;
        }

        Matrix4f base = new Matrix4f(pose.last().pose());
        Vector3f centerPivot = new Vector3f();
        float[] center = GunModelPivot.centerStable(model, stack);
        if (center != null) {
            base.transformPosition(center[0], center[1], center[2], centerPivot);
        }
        Vector3f pivot = new Vector3f(centerPivot).lerp(new Vector3f(0f, 0f, 0f), aimProgress);
        Matrix4f screen = new Matrix4f()
                .translate(pivot.x, pivot.y, pivot.z)
                .rotateZ((float) Math.toRadians(smoothedAngle))
                .translate(-pivot.x, -pivot.y, -pivot.z);
        Matrix4f local = new Matrix4f(base).invert().mul(screen).mul(base);
        pose.pushPose();
        pose.mulPose(local);
        return true;
    }

    private static float aimProgress(LocalPlayer player) {
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, operator.getClientAimingProgress(
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))));
    }
}
