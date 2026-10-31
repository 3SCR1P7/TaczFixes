package com.ssscript.taczfixes.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.GunBlocking;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * blocking 模型表现: 非双持向左旋转, 双持向上旋转, 均以 positioning/ground 组为中心并向后退。
 * 旋转与后退都在世界(视线)空间定义, 再映射到当前姿态空间, 因此与手部姿态/镜像无关; 强度做平滑过渡。
 */
public final class BlockingModelTransform {

    private BlockingModelTransform() {
    }

    /** 应用阻挡变换; 返回 true 表示已 pushPose, 调用方需负责 popPose。 */
    public static boolean apply(PoseStack pose, BedrockGunModel model, ItemStack gunStack,
                                LocalPlayer player, boolean forceDual) {
        if (pose == null || model == null || gunStack == null || player == null) {
            return false;
        }
        // 因子由 GunBlocking 统一平滑: 客户端每帧更新, 服务端每 tick 更新, 子弹与模型共用同一值。
        float factor = GunBlocking.updateAndGetFactor(player, forceDual);
        if (factor <= 0.001f) {
            return false;
        }
        GunTaczFixesData.BlockingConfig cfg = GunBlocking.resolve(gunStack);
        float[] center = GunModelPivot.center(model, gunStack);
        if (center == null) {
            return false;
        }
        double angle = Math.toRadians(GunBlocking.angleDeg(cfg) * factor);
        double back = GunBlocking.backOff(cfg) * factor;
        boolean dual = forceDual || DualWieldEligibility.isDualWielding(player);
        // 水平偏移仅在非双持时生效; 负数向左, 正数向右(视线空间 +X)
        double side = dual ? 0.0d : GunBlocking.offsetYaw(cfg, gunStack) * factor;
        // 1.21.1 第一人称姿态空间与 1.20 不同, 直接在模型局部空间绕枢轴旋转:
        // facing 90 = 绕局部 X 轴抬头/低头, facing 180 = 绕局部 Y 轴左右偏转。
        double facingRad = Math.toRadians(dual ? GunBlocking.facingDual(cfg) : GunBlocking.facing(cfg, gunStack));
        Vector3f axis = new Vector3f((float) Math.sin(facingRad), (float) -Math.cos(facingRad), 0.0f);
        if (axis.lengthSquared() < 1.0E-8f) {
            return false;
        }
        Quaternionf rotation = new Quaternionf().rotationAxis((float) -angle, axis.normalize());
        pose.pushPose();
        pose.translate((float) side, 0.0f, (float) back);
        pose.translate(center[0], center[1], center[2]);
        pose.mulPose(rotation);
        pose.translate(-center[0], -center[1], -center[2]);
        return true;
    }
}
