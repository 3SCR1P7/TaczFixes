package com.ssscript.taczfixes.client.attachment;

import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * 配件动画 camera 组：把配件动画里 camera 组的旋转叠加到第一人称镜头和手部。
 *
 * <p>和 TaCZ 枪械动画的 camera 组表现完全一致，TaCZ 自己也是两处一起加：
 * {@link ViewportEvent.ComputeCameraAngles} 里把相机四元数按同一套欧拉角分解叠加到视角，
 * {@link BeforeRenderHandEvent} 里把同一四元数乘到第一人称手部 {@code PoseStack} 上。
 * 只加手部的话，因为手占屏很小、又缺少参照物，几乎看不出"镜头在动"；
 * 两处同时加才是玩家看到的"枪械 camera 组"效果，且手和枪会跟着视角一起动。</p>
 *
 * <p>两个事件都排在 TaCZ 之后（{@link EventPriority#LOW}），属于在枪械自身相机动画之上再叠加一层。</p>
 */
public final class AttachmentAnimationCameraHandler {
    private static final double EPSILON = 1.0E-6D;

    /** 第一人称手部：跟随镜头做同样的旋转。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBeforeRenderHand(BeforeRenderHandEvent event) {
        Quaternionf rotation = currentRotation();
        if (rotation == null) {
            return;
        }
        event.getPoseStack().mulPose(rotation);
    }

    /** 视角：按 TaCZ 枪械 camera 组相同的欧拉角分解叠加到摄像机参数。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        Quaternionf rotation = currentRotation();
        if (rotation == null) {
            return;
        }
        double x = rotation.x();
        double y = rotation.y();
        double z = rotation.z();
        double w = rotation.w();
        // 与 com.tacz.guns.client.event.CameraSetupEvent 处理枪械 camera 组时的分解方式完全一致
        double yaw = Math.toDegrees(Math.asin(clamp(2.0D * ((w * y) - (x * z)))));
        double pitch = Math.toDegrees(Math.atan2(
                2.0D * ((w * x) + (y * z)),
                1.0D - (2.0D * ((x * x) + (y * y)))));
        double roll = Math.toDegrees(Math.atan2(
                2.0D * ((w * z) + (x * y)),
                1.0D - (2.0D * ((y * y) + (z * z)))));
        if (Math.abs(yaw) > EPSILON) {
            event.setYaw(event.getYaw() + (float) yaw);
        }
        if (Math.abs(pitch) > EPSILON) {
            event.setPitch(event.getPitch() + (float) pitch);
        }
        if (Math.abs(roll) > EPSILON) {
            event.setRoll(event.getRoll() + (float) roll);
        }
    }

    @Nullable
    private static Quaternionf currentRotation() {
        ItemStack stack;
        try {
            stack = KeepingItemRenderer.getRenderer().getCurrentItem();
        } catch (Throwable ignored) {
            return null;
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return AttachmentAnimationManager.cameraRotation(stack);
    }

    private static double clamp(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }
}
