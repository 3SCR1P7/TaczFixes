package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualThirdPersonRenderer.class */
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
        boolean recoilPosePushed = false;
        if (offhand) {
            handPhase = DualRenderContext.HandPhase.OFFHAND;
        } else {
            handPhase = DualRenderContext.HandPhase.MAIN;
        }
        DualRenderContext.setPhase(handPhase);
        if (offhand) {
            try {
                if (isLocalPlayerRender(entity)) {
                    poseStack.pushPose();
                    recoilPosePushed = true;
                    OffhandCameraController.applyModelRecoil(poseStack, stack, true);
                }
            } catch (Throwable th) {
                if (recoilPosePushed) {
                    poseStack.popPose();
                }
                if (previousPhase == DualRenderContext.HandPhase.NONE) {
                    DualRenderContext.clear();
                } else {
                    DualRenderContext.setPhase(previousPhase);
                }
                throw th;
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, packedLight, packedOverlay, poseStack, buffer, entity.level(), entity.getId());
        if (recoilPosePushed) {
            poseStack.popPose();
        }
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
}
