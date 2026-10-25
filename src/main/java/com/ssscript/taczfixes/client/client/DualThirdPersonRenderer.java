package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.client.resource.pojo.display.gun.GunTransform;
import com.ssscript.taczfixes.client.mixin.MixinGunDisplayInstanceAccessor;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import org.joml.Vector3f;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualThirdPersonRenderer {
    private DualThirdPersonRenderer() {
    }

    public static boolean isDualMode(LivingEntity entity) {
        return DualWieldEligibility.isDualWielding(entity);
    }

    public static boolean usesAnimatedOffhand(LivingEntity entity) {
        if (entity == null || IGun.getIGunOrNull(entity.getOffhandItem()) == null) {
            return false;
        }
        if (DualWieldEligibility.isDualWielding(entity)) {
            return true;
        }
        return IGun.getIGunOrNull(entity.getMainHandItem()) == null;
    }

    public static boolean renderOffhand(LivingEntity entity, ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        return renderStack(entity, stack, poseStack, buffer, packedLight, packedOverlay, true);
    }

    public static boolean renderMainhand(LivingEntity entity, ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        return renderStack(entity, stack, poseStack, buffer, packedLight, packedOverlay, false);
    }

    private static boolean renderStack(LivingEntity entity, ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, boolean offhand) {
        DualRenderContext.HandPhase handPhase;
        if (stack.isEmpty() || IGun.getIGunOrNull(stack) == null) {
            return false;
        }
        DualRenderContext.HandPhase previousPhase = DualRenderContext.getPhase();
        if (offhand) {
            handPhase = DualRenderContext.HandPhase.OFFHAND;
        } else {
            handPhase = DualRenderContext.HandPhase.MAIN;
        }
        DualRenderContext.setPhase(handPhase);
        if (offhand) {
            OffhandDisplayManager.updateThirdPersonAnimation(stack, 1.0F);
            GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
            BedrockGunModel model = display == null ? null : display.getGunModel();
            if (model != null) {
                applyMinigunPitchCorrection(entity, stack, true, poseStack);
                renderOffhandModel(display, model, stack, poseStack, buffer, packedLight, packedOverlay);
                if (previousPhase == DualRenderContext.HandPhase.NONE) {
                    DualRenderContext.clear();
                } else {
                    DualRenderContext.setPhase(previousPhase);
                }
                return true;
            }
        }
        applyMinigunPitchCorrection(entity, stack, offhand, poseStack);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, packedLight, packedOverlay, poseStack, buffer, entity.level(), entity.getId());
        if (previousPhase == DualRenderContext.HandPhase.NONE) {
            DualRenderContext.clear();
        } else {
            DualRenderContext.setPhase(previousPhase);
        }
        return true;
    }

    /* minigun 动画的枪械在双持第三人称使用 default 持枪动画时, 按两种动画的手臂俯仰差补偿枪口方向。 */
    private static void renderOffhandModel(GunDisplayInstance display, BedrockGunModel model, ItemStack stack,
                                           PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                           int packedOverlay) {
        GunTransform transform = display.getTransform();
        TransformScale scale = transform == null ? null : transform.getScale();
        poseStack.pushPose();
        try {
            poseStack.translate(0.5D, 2.0D, 0.5D);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            applyPositioningTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, scale, model, poseStack);
            applyScaleTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, scale, poseStack);
            RenderType renderType = display.enablesTransparency()
                    ? RenderType.entityTranslucent(display.getModelTexture())
                    : RenderType.entityCutout(display.getModelTexture());
            model.render(poseStack, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, renderType, packedLight, packedOverlay);
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyPositioningTransform(ItemDisplayContext context, TransformScale scale,
                                                  BedrockGunModel model, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        switch (context) {
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND ->
                    applyPositioningNodeTransform(model.getThirdPersonHandOriginPath(), poseStack, scale.getThirdPerson());
            default -> {
            }
        }
    }

    private static void applyScaleTransform(ItemDisplayContext context, TransformScale scale, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        Vector3f vector = switch (context) {
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> scale.getThirdPerson();
            default -> null;
        };
        if (vector != null) {
            poseStack.translate(0.0D, 1.5D, 0.0D);
            poseStack.scale(vector.x(), vector.y(), vector.z());
            poseStack.translate(0.0D, -1.5D, 0.0D);
        }
    }

    private static void applyPositioningNodeTransform(List<BedrockPart> path, PoseStack poseStack, Vector3f scale) {
        if (path == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1.0F, 1.0F, 1.0F);
        }
        poseStack.translate(0.0D, 1.5D, 0.0D);
        for (int index = path.size() - 1; index >= 0; index--) {
            BedrockPart part = path.get(index);
            poseStack.mulPose(Axis.XN.rotation(part.xRot));
            poseStack.mulPose(Axis.YN.rotation(part.yRot));
            poseStack.mulPose(Axis.ZN.rotation(part.zRot));
            if (part.getParent() != null) {
                poseStack.translate(-part.x * scale.x() / 16.0F, -part.y * scale.y() / 16.0F,
                        -part.z * scale.z() / 16.0F);
            } else {
                poseStack.translate(-part.x * scale.x() / 16.0F, (1.5F - part.y / 16.0F) * scale.y(),
                        -part.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0.0D, -1.5D, 0.0D);
    }

    private static void applyMinigunPitchCorrection(LivingEntity entity, ItemStack stack, boolean offhand, PoseStack poseStack) {
        GunDisplayInstance display = offhand
                ? OffhandDisplayManager.getOrCreate(stack)
                : (GunDisplayInstance) TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null) {
            return;
        }
        String rawAnimation = ((MixinGunDisplayInstanceAccessor) display).dualWield$getThirdPersonAnimationRaw();
        if (rawAnimation == null || !rawAnimation.toLowerCase(Locale.ROOT).contains("minigun")) {
            return;
        }
        float correction = minigunPitchDeltaDegrees(entity);
        if (correction != 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(correction));
        }
    }

    private static float minigunPitchDeltaDegrees(LivingEntity entity) {
        ModelPart rightMinigun = new ModelPart(List.of(), Map.of());
        ThirdPersonManager.getAnimation("minigun").animateGunHold(entity, rightMinigun, new ModelPart(List.of(), Map.of()), new ModelPart(List.of(), Map.of()), new ModelPart(List.of(), Map.of()));
        ModelPart rightDefault = new ModelPart(List.of(), Map.of());
        ThirdPersonManager.getAnimation("default").animateGunHold(entity, rightDefault, new ModelPart(List.of(), Map.of()), new ModelPart(List.of(), Map.of()), new ModelPart(List.of(), Map.of()));
        return (float) Math.toDegrees(rightDefault.xRot - rightMinigun.xRot);
    }
}
