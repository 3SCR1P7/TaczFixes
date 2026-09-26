package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualThirdPersonArmPose {
    private DualThirdPersonArmPose() {
    }

    public static boolean mirrorRightArm(LivingEntity entity, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
        if (!DualWieldEligibility.isDualWielding(entity) || entity.getPose() == Pose.SLEEPING || entity.onClimbable() || entity.isSwimming() || entity.getPose() == Pose.FALL_FLYING) {
            return false;
        }
        leftArm.x = -rightArm.x;
        leftArm.y = rightArm.y;
        leftArm.z = rightArm.z;
        leftArm.xRot = rightArm.xRot;
        leftArm.yRot = (2.0f * head.yRot) - rightArm.yRot;
        leftArm.zRot = -rightArm.zRot;
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
