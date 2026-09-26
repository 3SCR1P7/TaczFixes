package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.mixin.MixinGunDisplayInstanceAccessor;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualThirdPersonRenderer {
    private DualThirdPersonRenderer() {
    }

    public static boolean isDualMode(LivingEntity entity) {
        return DualWieldEligibility.isDualWielding(entity);
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
        applyMinigunPitchCorrection(entity, stack, offhand, poseStack);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, packedLight, packedOverlay, poseStack, buffer, entity.level(), entity.getId());
        if (previousPhase == DualRenderContext.HandPhase.NONE) {
            DualRenderContext.clear();
        } else {
            DualRenderContext.setPhase(previousPhase);
        }
        return true;
    }

    private static boolean isLocalPlayerRender(LivingEntity entity) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        return (localPlayer == null || entity == null || (entity != localPlayer && !entity.getUUID().equals(localPlayer.getUUID()))) ? false : true;
    }

    /* minigun 动画的枪械在双持第三人称使用 default 持枪动画时, 按两种动画的手臂俯仰差补偿枪口方向。 */
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
