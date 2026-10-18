package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.renderer.item.GunItemRendererWrapper", remap = false)
public abstract class MixinGunRendererMovementRoll {
    @Unique
    private static float taczfixes$rollAngle;
    @Unique
    private static long taczfixes$rollLastNanos;

    @Inject(method = "renderFirstPerson", at = @At("HEAD"), remap = false)
    private void taczfixes$rollHead(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                    PoseStack pose, MultiBufferSource buffers, int light, float partial,
                                    CallbackInfo ci) {
        pose.pushPose();
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || IGun.getIGunOrNull(stack) == null
                || DualWieldClient.isDualMode(player)) {
            return;
        }
        float maxAngle = Config.MOVEMENT_ROLL_MAX_ANGLE.get().floatValue();
        float fullSpeed = Config.MOVEMENT_ROLL_FULL_SPEED.get().floatValue();
        if (maxAngle <= 0f || fullSpeed <= 0f) {
            return;
        }
        long now = System.nanoTime();
        if (taczfixes$rollLastNanos == 0L) {
            taczfixes$rollLastNanos = now;
        }
        float dt = Math.min((now - taczfixes$rollLastNanos) / 1.0e9f, 0.1f);
        taczfixes$rollLastNanos = now;
        Vec3 view = player.getViewVector(partial);
        Vec3 right = new Vec3(-view.z, 0.0d, view.x);
        float target = 0f;
        if (right.lengthSqr() > 1.0E-8d) {
            right = right.normalize();
            double lateral = player.getDeltaMovement().dot(right) * 20.0d;
            target = (float) (-lateral / fullSpeed * maxAngle);
            target = Math.max(-maxAngle, Math.min(maxAngle, target));
        }
        float alpha = 1.0f - (float) Math.exp(-12.0f * dt);
        taczfixes$rollAngle += (target - taczfixes$rollAngle) * alpha;
        if (Math.abs(taczfixes$rollAngle) < 0.01f) {
            return;
        }
        pose.mulPose(Axis.ZP.rotation((float) Math.toRadians(taczfixes$rollAngle)));
    }

    @Inject(method = "renderFirstPerson", at = @At("RETURN"), remap = false)
    private void taczfixes$rollReturn(LocalPlayer player, ItemStack stack, ItemDisplayContext context,
                                      PoseStack pose, MultiBufferSource buffers, int light, float partial,
                                      CallbackInfo ci) {
        pose.popPose();
    }
}
