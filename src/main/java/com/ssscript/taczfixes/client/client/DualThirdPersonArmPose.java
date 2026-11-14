package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualThirdPersonArmPose {
    private DualThirdPersonArmPose() {
    }

    private static void applyGunAnimationToBothArms(LivingEntity entity, ModelPart rightArm, ModelPart leftArm,
                                                    ModelPart body, ModelPart head) {
        GunDisplayInstance display = TimelessAPI.getGunDisplay(entity.getOffhandItem()).orElse(null);
        if (display == null) {
            return;
        }
        com.tacz.guns.api.client.other.IThirdPersonAnimation animation =
                ThirdPersonManager.getAnimation(display.getThirdPersonAnimation());
        if (animation == null) {
            return;
        }
        com.tacz.guns.api.entity.IGunOperator operator =
                com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(entity);
        float aimingProgress = operator == null ? 0.0f : operator.getSynAimingProgress();
        if (aimingProgress > 0.0f) {
            animation.animateGunAim(entity, rightArm, leftArm, body, head, aimingProgress);
        } else {
            animation.animateGunHold(entity, rightArm, leftArm, body, head);
        }
    }
    public static boolean mirrorRightArm(LivingEntity entity, ModelPart body, ModelPart head,
                                         ModelPart rightArm, ModelPart leftArm) {
        var offhand = entity.getOffhandItem();
        boolean mainGun = com.tacz.guns.api.item.IGun.getIGunOrNull(entity.getMainHandItem()) != null;
        boolean offGun = com.tacz.guns.api.item.IGun.getIGunOrNull(offhand) != null;
        if (offhand.isEmpty() || (!mainGun && !offGun)) {
            return false;
        }
        if (offGun) {
            if (entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem
                    || offhand.getItem() instanceof net.minecraft.world.item.ShieldItem
                    || entity.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
                return false;
            }
            if (!DualWieldEligibility.isDualWielding(entity) && mainGun) {
                return false;
            }
        } else if (!mainGun || entity.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            return false;
        }
        if (entity.getPose() == Pose.SLEEPING || entity.onClimbable() || entity.isSwimming()
                || entity.getPose() == Pose.FALL_FLYING) {
            return false;
        }
        if (offGun && !mainGun) {
            // 主手物品、副手枪: 有 playeranimator 资源时由 playeranimator 层摆臂(与双手持枪完全一致);
            // 没有资源时才退回 TaCZ 的 animateGunHold 兜底。
            GunDisplayInstance offDisplay = TimelessAPI.getGunDisplay(offhand).orElse(null);
            boolean animatedByPlayerAnimator = offDisplay != null
                    && com.tacz.guns.compat.playeranimator.animation.AnimationManager.hasPlayerAnimator3rd(offDisplay);
            if (!animatedByPlayerAnimator) {
                applyGunAnimationToBothArms(entity, rightArm, leftArm, body, head);
            }
        }
        // 与双手持枪一致: 右臂(或 playeranimator 后的右臂)镜像到左臂。
        leftArm.x = -rightArm.x;
        leftArm.y = rightArm.y;
        leftArm.z = rightArm.z;
        leftArm.xRot = rightArm.xRot;
        leftArm.yRot = (2.0f * head.yRot) - rightArm.yRot;
        leftArm.zRot = -rightArm.zRot;
        if (entity.swinging && entity.swingingArm == InteractionHand.OFF_HAND) {
            float attack = entity.getAttackAnim(Minecraft.getInstance().getFrameTime());
            if (attack > 0.0F) {
                float f1 = 1.0F - attack;
                f1 *= f1;
                f1 *= f1;
                f1 = 1.0F - f1;
                float f2 = Mth.sin(f1 * (float) Math.PI);
                float f3 = Mth.sin(attack * (float) Math.PI) * -(head.xRot - 0.7F) * 0.75F;
                leftArm.xRot -= f2 * 1.2F + f3;
                leftArm.yRot -= Mth.sin(attack * (float) Math.PI) * 0.4F;
                leftArm.zRot -= Mth.sin((1.0F - (1.0F - attack) * (1.0F - attack)) * (float) Math.PI) * 0.6F;
            }
        }
        float focusProgress = DualFocusAimState.getEntityProgress(entity);
        if (focusProgress > 0.0f) {
            leftArm.xRot = Mth.lerp(focusProgress, leftArm.xRot, 0.0f);
            leftArm.yRot = Mth.lerp(focusProgress, leftArm.yRot, 0.0f);
            leftArm.zRot = Mth.lerp(focusProgress, leftArm.zRot, 0.0f);
            return true;
        }
        return true;
    }
}
