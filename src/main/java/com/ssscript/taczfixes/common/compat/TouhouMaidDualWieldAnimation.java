package com.ssscript.taczfixes.common.compat;

import com.github.tartaricacid.touhoulittlemaid.api.animation.ICustomAnimation;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IModelRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.DualFocusAimState;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/compat/TouhouMaidDualWieldAnimation.class */
public final class TouhouMaidDualWieldAnimation implements ICustomAnimation<Mob> {
    private static final String LEFT_ARM = "armLeft";
    private static final String RIGHT_ARM = "armRight";
    private static final String GECKO_LEFT_ARM = "LeftArm";
    private static final String GECKO_RIGHT_ARM = "RightArm";
    private static final String GECKO_LEFT_FOREARM = "LeftForeArm";
    private static final String GECKO_RIGHT_FOREARM = "RightForeArm";
    private static final String GECKO_LEFT_HAND = "LeftHand";
    private static final String GECKO_RIGHT_HAND = "RightHand";
    private static final String GECKO_LEFT_HAND_LOCATOR = "LeftHandLocator";
    private static final String GECKO_RIGHT_HAND_LOCATOR = "RightHandLocator";
    private static final double FALLBACK_GUN_DROP_Y = -0.18d;
    private static final double FALLBACK_GUN_DROP_Z = 0.05d;



    public void setRotationAngles(Mob maid, HashMap<String, ? extends IModelRenderer> models, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        mirrorBedrockPose(maid, models);
    }

    public static void mirrorBedrockPose(Mob maid, Map<String, ? extends IModelRenderer> models) {
        if (!DualWieldEligibility.isDualWielding(maid)) {
            return;
        }
        IModelRenderer leftArm = models.get(LEFT_ARM);
        IModelRenderer rightArm = models.get(RIGHT_ARM);
        mirrorBedrockPart(rightArm, leftArm);
        applyBedrockFocusLowReady(maid, leftArm);
    }

    private static void mirrorBedrockPart(IModelRenderer right, IModelRenderer left) {
        if (left == null || right == null) {
            return;
        }
        left.setOffsetX(-right.getOffsetX());
        left.setOffsetY(right.getOffsetY());
        left.setOffsetZ(right.getOffsetZ());
        left.setRotateAngleX(right.getRotateAngleX());
        left.setRotateAngleY(-right.getRotateAngleY());
        left.setRotateAngleZ(-right.getRotateAngleZ());
    }

    public void setGeckoRotationAngles(Mob maid, AnimatedGeoModel model, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        mirrorGeckoPose(maid, model);
    }

    public static void mirrorGeckoPose(Mob maid, AnimatedGeoModel model) {
        if (!DualWieldEligibility.isDualWielding(maid)) {
            return;
        }
        mirrorGeckoBone(model, GECKO_RIGHT_ARM, GECKO_LEFT_ARM);
        mirrorGeckoBone(model, GECKO_RIGHT_FOREARM, GECKO_LEFT_FOREARM);
        mirrorGeckoBone(model, GECKO_RIGHT_HAND, GECKO_LEFT_HAND);
        mirrorGeckoBone(model, GECKO_RIGHT_HAND_LOCATOR, GECKO_LEFT_HAND_LOCATOR);
        float focusProgress = DualFocusAimState.getEntityProgress(maid);
        applyGeckoFocusLowReady((AnimatedGeoBone) model.bones().get(GECKO_LEFT_ARM), focusProgress);
        applyGeckoFocusLowReady((AnimatedGeoBone) model.bones().get(GECKO_LEFT_FOREARM), focusProgress);
        applyGeckoFocusLowReady((AnimatedGeoBone) model.bones().get(GECKO_LEFT_HAND), focusProgress);
    }

    public static void applyFallbackOffhandLowReady(Mob maid, PoseStack poseStack) {
        float progress = DualFocusAimState.getEntityProgress(maid);
        if (poseStack == null || progress <= 0.0f) {
            return;
        }
        poseStack.translate(0.0d, FALLBACK_GUN_DROP_Y * progress, FALLBACK_GUN_DROP_Z * progress);
    }

    private static void applyBedrockFocusLowReady(Mob maid, IModelRenderer leftArm) {
        float progress = DualFocusAimState.getEntityProgress(maid);
        if (leftArm == null || progress <= 0.0f) {
            return;
        }
        leftArm.setRotateAngleX(Mth.lerp(progress, leftArm.getRotateAngleX(), leftArm.getInitRotateAngleX()));
        leftArm.setRotateAngleY(Mth.lerp(progress, leftArm.getRotateAngleY(), leftArm.getInitRotateAngleY()));
        leftArm.setRotateAngleZ(Mth.lerp(progress, leftArm.getRotateAngleZ(), leftArm.getInitRotateAngleZ()));
    }

    private static void applyGeckoFocusLowReady(AnimatedGeoBone bone, float progress) {
        BoneSnapshot initial;
        if (bone == null || progress <= 0.0f || (initial = bone.getInitialSnapshot()) == null) {
            return;
        }
        bone.setRotationX(Mth.lerp(progress, bone.getRotationX(), initial.rotationValueX));
        bone.setRotationY(Mth.lerp(progress, bone.getRotationY(), initial.rotationValueY));
        bone.setRotationZ(Mth.lerp(progress, bone.getRotationZ(), initial.rotationValueZ));
    }

    private static void mirrorGeckoBone(AnimatedGeoModel model, String rightName, String leftName) {
        AnimatedGeoBone right = (AnimatedGeoBone) model.bones().get(rightName);
        AnimatedGeoBone left = (AnimatedGeoBone) model.bones().get(leftName);
        if (right == null || left == null) {
            return;
        }
        left.setRotationX(right.getRotationX());
        left.setRotationY(-right.getRotationY());
        left.setRotationZ(-right.getRotationZ());
        left.setPositionX(-right.getPositionX());
        left.setPositionY(right.getPositionY());
        left.setPositionZ(right.getPositionZ());
        left.setScaleX(right.getScaleX());
        left.setScaleY(right.getScaleY());
        left.setScaleZ(right.getScaleZ());
    }
}
