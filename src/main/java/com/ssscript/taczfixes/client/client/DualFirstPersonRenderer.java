package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.client.DualRenderContext;
import com.ssscript.taczfixes.common.compat.CharmsOffhandRenderCompat;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.client.mixin.MixinGunItemRendererWrapperAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class DualFirstPersonRenderer {
    private DualFirstPersonRenderer() {
    }

    public static boolean renderOffhand(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick) {
        if (!(stack.getItem() instanceof IGun)) {
            return false;
        }
        if (DualReloadAnimationManager.areArmsHidden(net.minecraft.world.InteractionHand.OFF_HAND)) {
            OffhandDisplayManager.updateAnimation(stack, partialTick);
            return true;
        }
        GunDisplayInstance display;
        try {
            display = OffhandDisplayManager.getOrCreate(stack);
        } catch (RuntimeException exception) {
            TaczFixesMod.LOGGER.error("Failed to resolve independent offhand assets; using fallback renderer", exception);
            return false;
        }
        BedrockGunModel model = display == null ? null : display.getGunModel();
        if (model == null) {
            return false;
        }
        boolean posePushed = false;
        boolean renderHandSaved = false;
        boolean renderHandValue = false;
        boolean mirroredModel = false;
        boolean cullWasEnabled = false;
        boolean recolorPushed = false;
        boolean charmsFrame = false;
        boolean selfFlagsSet = false;
        try {
            OffhandCameraController.applyPendingRecoil(player);
            OffhandDisplayManager.updateAnimation(stack, partialTick);
            OffhandCameraController.captureFirstPersonShotCamera(player, stack, model);
            DualRenderContext.setPhase(DualRenderContext.HandPhase.OFFHAND);
            poseStack.pushPose();
            posePushed = true;
            renderHandValue = model.getRenderHand();
            OffhandCameraController.applyModelCameraAnimation(player, stack, model, poseStack);
            poseStack.translate(DualWieldEligibility.getClientLeftGunXOffset(), 0.0d, 0.0d);
            float xRotOffset = Mth.lerp(partialTick, player.xBobO, player.xBob);
            float yRotOffset = Mth.lerp(partialTick, player.yBobO, player.yBob);
            float xRot = player.getViewXRot(partialTick) - xRotOffset;
            float yRot = player.getViewYRot(partialTick) - yRotOffset;
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot * (-0.1f)));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot * (-0.1f)));
            BedrockPart root = model.getRootNode();
            if (root != null) {
                float xRot2 = ((float) Math.tanh(xRot / 25.0f)) * 25.0f;
                float yRot2 = ((float) Math.tanh(yRot / 25.0f)) * 25.0f;
                root.offsetX += (((-yRot2) * 0.1f) / 16.0f) / 3.0f;
                root.offsetY += (((-xRot2) * 0.1f) / 16.0f) / 3.0f;
                root.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot2 * 0.05f));
                root.additionalQuaternion.mul(Axis.YN.rotationDegrees(yRot2 * 0.05f));
            }
            poseStack.translate(0.0d, 1.5d, 0.0d);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            mirroredModel = DualRenderContext.offhandHandPos().mirror();
            AnimateGeoItemRenderer.applyFirstPersonPositioningTransform(poseStack, model, stack);
            if (mirroredModel) {
                poseStack.scale(-1.0f, 1.0f, 1.0f);
            }
            DualFocusAimState.applyFirstPersonOffhandLowReady(poseStack, partialTick);
            DualRenderContext.captureOffhandModelBase(model, poseStack.last().pose());
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
            selfFlagsSet = true;
            if (RefitTransform.getOpeningProgress() != 0.0f) {
                model.setRenderHand(false);
                renderHandSaved = true;
            }
            // 调色: 该路径不经过 GunItemRendererWrapper 的渲染上下文, 需手动 push/pop 副手枪械
            com.ssscript.taczfixes.client.util.GunRecolorManager.pushRenderStack(stack);
            recolorPushed = true;
            RenderType renderType = display.enablesTransparency() ? RenderType.entityTranslucent(display.getModelTexture()) : RenderType.entityCutout(display.getModelTexture());
            charmsFrame = CharmsOffhandRenderCompat.beginFrame(stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, bufferSource, light, OverlayTexture.NO_OVERLAY, partialTick);
            if (mirroredModel) {
                MultiBufferSource.BufferSource sink = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
                sink.endBatch();
                cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                RenderSystem.enableCull();
                GL11.glFrontFace(GL11.GL_CW);
                model.render(poseStack, stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, renderType, light, OverlayTexture.NO_OVERLAY);
                sink.endBatch();
            } else {
                model.render(poseStack, stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, renderType, light, OverlayTexture.NO_OVERLAY);
            }
            com.ssscript.taczfixes.client.util.GunRecolorManager.popRenderStack();
            recolorPushed = false;
            if (charmsFrame) {
                CharmsOffhandRenderCompat.renderCaptured(poseStack);
            }
            OffhandCameraController.captureFirstPersonShotCamera(player, stack, model);
            if (model.getMuzzleFlashPosPath() == null || model.getMuzzleFlashPosPath().isEmpty()) {
                DualMuzzleFlashState.invalidateMuzzle(DualRenderContext.HandPhase.OFFHAND);
                com.ssscript.taczfixes.client.util.CustomScopeViewShift.pop(poseStack);
            } else {
                Vector3f previousMuzzle = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                try {
                    MixinGunItemRendererWrapperAccessor.dualWield$cacheMuzzlePosition(poseStack, model);
                } finally {
                    GunItemRendererWrapper.muzzleRenderOffset.set(previousMuzzle);
                }
            }
            return true;
        } catch (RuntimeException | Error exception) {
            TaczFixesMod.LOGGER.error("Failed to render independent offhand gun; using fallback renderer", exception);
            return false;
        } finally {
            if (recolorPushed) {
                com.ssscript.taczfixes.client.util.GunRecolorManager.popRenderStack();
            }
            if (mirroredModel) {
                if (cullWasEnabled) {
                    RenderSystem.enableCull();
                } else {
                    RenderSystem.disableCull();
                }
                GL11.glFrontFace(GL11.GL_CCW);
            }
            if (selfFlagsSet) {
                MuzzleFlashRender.isSelf = false;
                ShellRender.isSelf = false;
            }
            DualRenderContext.clear();
            if (renderHandSaved) {
                try {
                    model.setRenderHand(renderHandValue);
                } catch (RuntimeException exception) {
                    TaczFixesMod.LOGGER.error("Failed to restore offhand hand-render flag", exception);
                }
            }
            try {
                model.cleanAnimationTransform();
            } catch (RuntimeException exception) {
                TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception);
            }
            if (posePushed) {
                try {
                    poseStack.popPose();
                } catch (RuntimeException exception) {
                    TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception);
                }
            }
            if (charmsFrame) {
                try {
                    CharmsOffhandRenderCompat.endFrame();
                } catch (RuntimeException exception) {
                    TaczFixesMod.LOGGER.error("Failed to finish offhand charms frame", exception);
                }
            }
        }
    }
}
