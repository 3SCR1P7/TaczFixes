package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.DualRenderContext;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualFirstPersonRenderer.class */
public final class DualFirstPersonRenderer {
    private DualFirstPersonRenderer() {
    }
    public static boolean renderOffhand(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick) {
        if (!(stack.getItem() instanceof IGun)) {
            return false;
        }
        try {
            GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
            BedrockGunModel model = display == null ? null : display.getGunModel();
            if (model == null) {
                return false;
            }
            boolean posePushed = false;
            try {
                try {
                    OffhandCameraController.applyPendingRecoil(player);
                    OffhandDisplayManager.updateAnimation(stack, partialTick);
                    DualRenderContext.setPhase(DualRenderContext.HandPhase.OFFHAND);
                    poseStack.pushPose();
                    posePushed = true;
                    boolean renderHand = model.getRenderHand();
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
                    AnimateGeoItemRenderer.applyFirstPersonPositioningTransform(poseStack, model, stack);
                    DualFocusAimState.applyFirstPersonOffhandLowReady(poseStack, partialTick);
                    DualRenderContext.captureOffhandModelBase(model, poseStack.last().pose());
                    MuzzleFlashRender.isSelf = true;
                    ShellRender.isSelf = true;
                    if (RefitTransform.getOpeningProgress() != 0.0f) {
                        model.setRenderHand(false);
                    }
                    RenderType renderType = display.enablesTransparency() ? RenderType.entityTranslucent(display.getModelTexture()) : RenderType.entityCutout(display.getModelTexture());
                    boolean charmsFrame = CharmsOffhandRenderCompat.beginFrame(stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, bufferSource, light, OverlayTexture.NO_OVERLAY, partialTick);
                    try {
                        model.render(poseStack, stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, renderType, light, OverlayTexture.NO_OVERLAY);
                        if (charmsFrame) {
                            CharmsOffhandRenderCompat.renderCaptured(poseStack);
                        }
                    } finally {
                        if (charmsFrame) {
                            CharmsOffhandRenderCompat.endFrame();
                        }
                    }
                    if (model.getMuzzleFlashPosPath() == null || model.getMuzzleFlashPosPath().isEmpty()) {
                        DualMuzzleFlashState.invalidateMuzzle(DualRenderContext.HandPhase.OFFHAND);
                        com.ssscript.taczfixes.client.util.CustomScopeViewShift.pop(poseStack);
                    } else {
                        Vector3f previousMuzzle = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                        try {
                            MixinGunItemRendererWrapperAccessor.dualWield$cacheMuzzlePosition(poseStack, model);
                            GunItemRendererWrapper.muzzleRenderOffset.set(previousMuzzle);
                        } catch (Throwable th) {
                            GunItemRendererWrapper.muzzleRenderOffset.set(previousMuzzle);
                            throw th;
                        }
                    }
                    MuzzleFlashRender.isSelf = false;
                    ShellRender.isSelf = false;
                    DualRenderContext.clear();
                    if (1 != 0) {
                        try {
                            try {
                                model.setRenderHand(renderHand);
                            } catch (RuntimeException exception) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand hand-render flag", exception);
                            }
                        } catch (Throwable th2) {
                            try {
                                try {
                                    model.cleanAnimationTransform();
                                } catch (RuntimeException exception2) {
                                    TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception2);
                                }
                                if (posePushed) {
                                    try {
                                        poseStack.popPose();
                                    } catch (RuntimeException exception3) {
                                        TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception3);
                                    }
                                }
                                throw th2;
                            } finally {
                            }
                        }
                    }
                    try {
                        try {
                            model.cleanAnimationTransform();
                        } finally {
                        }
                    } catch (RuntimeException exception4) {
                        TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception4);
                    }
                    if (posePushed) {
                        try {
                            poseStack.popPose();
                        } catch (RuntimeException exception5) {
                            TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception5);
                        }
                    }
                    return true;
                } catch (RuntimeException exception6) {
                    TaczFixesMod.LOGGER.error("Failed to render independent offhand gun; using fallback renderer", exception6);
                    MuzzleFlashRender.isSelf = false;
                    ShellRender.isSelf = false;
                    DualRenderContext.clear();
                    if (0 != 0) {
                        try {
                            try {
                                model.setRenderHand(false);
                            } catch (RuntimeException exception7) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand hand-render flag", exception7);
                            }
                        } catch (Throwable th3) {
                            try {
                                try {
                                    model.cleanAnimationTransform();
                                } catch (RuntimeException exception8) {
                                    TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception8);
                                }
                                if (posePushed) {
                                    try {
                                        poseStack.popPose();
                                    } catch (RuntimeException exception9) {
                                        TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception9);
                                    }
                                }
                                throw th3;
                            } finally {
                                if (posePushed) {
                                    try {
                                        poseStack.popPose();
                                    } catch (RuntimeException exception10) {
                                        TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception10);
                                    }
                                }
                            }
                        }
                    }
                    try {
                        try {
                            model.cleanAnimationTransform();
                        } catch (RuntimeException exception11) {
                            TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception11);
                        }
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception12) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception12);
                            }
                        }
                        return false;
                    } finally {
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception13) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception13);
                            }
                        }
                    }
                }
            } catch (Throwable th4) {
                MuzzleFlashRender.isSelf = false;
                ShellRender.isSelf = false;
                DualRenderContext.clear();
                try {
                    if (0 != 0) {
                        try {
                            model.setRenderHand(false);
                        } catch (RuntimeException exception14) {
                            TaczFixesMod.LOGGER.error("Failed to restore offhand hand-render flag", exception14);
                        }
                    }
                    try {
                        try {
                            model.cleanAnimationTransform();
                        } catch (RuntimeException exception15) {
                            TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception15);
                        }
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception16) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception16);
                            }
                        }
                        throw th4;
                    } finally {
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception17) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception17);
                            }
                        }
                    }
                } catch (Throwable th5) {
                    try {
                        try {
                            model.cleanAnimationTransform();
                        } catch (RuntimeException exception18) {
                            TaczFixesMod.LOGGER.error("Failed to clean independent offhand animation", exception18);
                        }
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception19) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception19);
                            }
                        }
                        throw th5;
                    } finally {
                        if (posePushed) {
                            try {
                                poseStack.popPose();
                            } catch (RuntimeException exception20) {
                                TaczFixesMod.LOGGER.error("Failed to restore offhand pose stack", exception20);
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException exception21) {
            TaczFixesMod.LOGGER.error("Failed to resolve independent offhand assets; using fallback renderer", exception21);
            return false;
        }
    }
}
