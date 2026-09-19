package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.render.DualFocusAimState;
import com.ssscript.taczfixes.client.render.DualMuzzleFlashState;
import com.ssscript.taczfixes.client.render.DualReloadAnimationManager;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import com.ssscript.taczfixes.common.util.DualWieldStackId;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {GunItemRendererWrapper.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinGunItemRendererWrapper.class */
public abstract class MixinGunItemRendererWrapper {

    @Unique
    private static final ThreadLocal<Integer> DUAL_WIELD_RENDER_INVOCATION_DEPTH = ThreadLocal.withInitial(() -> {
        return 0;
    });

    @Unique
    private static final ThreadLocal<Integer> DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH = ThreadLocal.withInitial(() -> {
        return 0;
    });

    @Redirect(method = {"renderByItem"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/TimelessAPI;getGunDisplay(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = true)
    private Optional<GunDisplayInstance> dualWield$useIndependentThirdPersonOffhandDisplay(ItemStack stack) {
        UUID uuid;
        GunDisplayInstance independent;
        LocalPlayer player = Minecraft.getInstance().player;
        UUID renderedStackId = DualWieldStackId.get(stack);
        if (player == null) {
            uuid = null;
        } else {
            uuid = DualWieldStackId.get(player.getOffhandItem());
        }
        UUID localOffhandStackId = uuid;
        if (DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND && player != null && renderedStackId != null && renderedStackId.equals(localOffhandStackId) && (independent = OffhandDisplayManager.getOrCreate(stack)) != null) {
            return Optional.of(independent);
        }
        return TimelessAPI.getGunDisplay(stack);
    }

    @Redirect(method = {"lambda$renderByItem$6"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/GunDisplayInstance;getLodModel()Lorg/apache/commons/lang3/tuple/Pair;", remap = false), require = ServerMessageOffhandActionResult.ACTION_RELOAD, allow = ServerMessageOffhandActionResult.ACTION_RELOAD, remap = false)
    private static Pair<BedrockGunModel, ResourceLocation> dualWield$forceThirdPersonFullModel(GunDisplayInstance invokedDisplay, ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, ItemStack stack, GunDisplayInstance display) {
        if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return null;
        }
        return invokedDisplay.getLodModel();
    }

    @WrapOperation(method = {"lambda$renderByItem$6"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", remap = false)}, require = ServerMessageOffhandActionResult.ACTION_RELOAD, allow = ServerMessageOffhandActionResult.ACTION_RELOAD, remap = false)
    private static void dualWield$scopeLocalThirdPersonMainMuzzle(BedrockGunModel model, PoseStack poseStack, ItemStack stack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay, float f1, float f2, float f3, float f4, MultiBufferSource.BufferSource bufferSource, Operation<Void> original) {
        DualRenderContext.HandPhase previousPhase = DualRenderContext.getPhase();
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack localMainStack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        UUID renderedStackId = DualWieldStackId.get(stack);
        UUID localMainStackId = DualWieldStackId.get(localMainStack);
        boolean assignedMain = previousPhase == DualRenderContext.HandPhase.NONE && transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && DualWieldClient.isDualMode(player) && stack == localMainStack && renderedStackId != null && renderedStackId.equals(localMainStackId);
        if (assignedMain) {
            DualRenderContext.setPhase(DualRenderContext.HandPhase.MAIN);
        }
        try {
            original.call(new Object[]{model, poseStack, stack, transformType, renderType, Integer.valueOf(light), Integer.valueOf(overlay), Float.valueOf(f1), Float.valueOf(f2), Float.valueOf(f3), Float.valueOf(f4), bufferSource});
            if (assignedMain) {
                DualRenderContext.clear();
            }
        } catch (Throwable th) {
            if (assignedMain) {
                DualRenderContext.clear();
            }
            throw th;
        }
    }

    @Inject(method = {"cacheMuzzlePosition"}, at = {@At("HEAD")})
    private static void dualWield$invalidatePerHandMuzzleBeforeCapture(PoseStack poseStack, BedrockGunModel model, CallbackInfo callback) {
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (phase == DualRenderContext.HandPhase.MAIN || phase == DualRenderContext.HandPhase.OFFHAND) {
            DualMuzzleFlashState.invalidateMuzzle(phase);
        }
    }

    @Inject(method = {"cacheMuzzlePosition"}, at = {@At("RETURN")})
    private static void dualWield$capturePerHandMuzzle(PoseStack poseStack, BedrockGunModel model, CallbackInfo callback) {
        DualRenderContext.HandPhase phase = DualRenderContext.getPhase();
        if (phase != DualRenderContext.HandPhase.MAIN && phase != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        if (model == null || model.getMuzzleFlashPosPath() == null || model.getMuzzleFlashPosPath().isEmpty()) {
            DualMuzzleFlashState.invalidateMuzzle(phase);
        } else {
            DualMuzzleFlashState.captureMuzzle(phase, GunItemRendererWrapper.muzzleRenderOffset);
        }
    }

    /** 双持换弹: 丢枪动画结束到掏枪动画开始之间只跳过主手枪械模型渲染, 其余流程照常。 */
    @WrapOperation(method = {"lambda$renderFirstPerson$5"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", remap = false)}, require = 0, remap = false)
    private void dualWield$hideMainGunWhileReloading(BedrockGunModel model, PoseStack poseStack, ItemStack stack, ItemDisplayContext transformType, RenderType renderType, int light, int overlay, MultiBufferSource.BufferSource bufferSource, Operation<Void> original) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DualWieldClient.isDualMode(player)
                && DualReloadAnimationManager.areArmsHidden(net.minecraft.world.InteractionHand.MAIN_HAND)) {
            return;
        }
        original.call(model, poseStack, stack, transformType, renderType, light, overlay, bufferSource);
    }

    @Inject(method = {"lambda$renderFirstPerson$5"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", shift = At.Shift.BEFORE, remap = false)}, require = 0, remap = false)
    private void dualWield$captureMainModelBase(ItemStack stack, LocalPlayer player, float partialTick, PoseStack poseStack, ItemDisplayContext context, MultiBufferSource bufferSource, int light, GunDisplayInstance display, CallbackInfo callback) {
        if (DualRenderContext.getPhase() != DualRenderContext.HandPhase.MAIN || display == null) {
            return;
        }
        DualRenderContext.captureMainModelBase(display.getGunModel(), poseStack.last().pose());
    }

    @Inject(method = {"lambda$renderFirstPerson$5"}, at = {@At("HEAD")})
    private void dualWield$enterMainRenderInvocation(ItemStack stack, LocalPlayer player, float partialTick, PoseStack poseStack, ItemDisplayContext context, MultiBufferSource bufferSource, int light, GunDisplayInstance display, CallbackInfo callback) {
        int invocationDepth = DUAL_WIELD_RENDER_INVOCATION_DEPTH.get().intValue();
        if (invocationDepth > 0 || DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.get().intValue() > 0) {
            DUAL_WIELD_RENDER_INVOCATION_DEPTH.remove();
            DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.remove();
            DualRenderContext.clear();
            invocationDepth = 0;
        }
        DUAL_WIELD_RENDER_INVOCATION_DEPTH.set(Integer.valueOf(invocationDepth + 1));
    }

    @Inject(method = {"lambda$renderFirstPerson$5"}, at = {@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, remap = true)})
    private void dualWield$beginMainRender(ItemStack stack, LocalPlayer player, float partialTick, PoseStack poseStack, ItemDisplayContext context, MultiBufferSource bufferSource, int light, GunDisplayInstance display, CallbackInfo callback) {
        int invocationDepth = DUAL_WIELD_RENDER_INVOCATION_DEPTH.get().intValue();
        if (invocationDepth <= 0 || DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.get().intValue() > 0) {
            return;
        }
        boolean isDualMainRender = DualWieldClient.isDualMode(player);
        boolean isPutAwayMainRender = OffhandDisplayManager.isPuttingAwayMainStack(stack);
        if ((!isDualMainRender && !isPutAwayMainRender) || DualRenderContext.getPhase() != DualRenderContext.HandPhase.NONE) {
            return;
        }
        DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.set(Integer.valueOf(invocationDepth));
        DualRenderContext.setPhase(DualRenderContext.HandPhase.MAIN);
        float focusProgress = DualFocusAimState.getProgress(partialTick);
        poseStack.translate(DualWieldOverrides.rightOffset(stack, DualWieldEligibility.getClientRightGunXOffset()) * (1.0f - focusProgress), 0.0d, 0.0d);
    }

    @Inject(method = {"lambda$renderFirstPerson$5"}, at = {@At("RETURN")})
    private void dualWield$leaveMainRenderInvocation(ItemStack stack, LocalPlayer player, float partialTick, PoseStack poseStack, ItemDisplayContext context, MultiBufferSource bufferSource, int light, GunDisplayInstance display, CallbackInfo callback) {
        int invocationDepth = DUAL_WIELD_RENDER_INVOCATION_DEPTH.get().intValue();
        if (DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.get().intValue() == invocationDepth) {
            DUAL_WIELD_ACTIVE_MAIN_RENDER_DEPTH.remove();
            DualRenderContext.clear();
        }
        if (invocationDepth <= 1) {
            DUAL_WIELD_RENDER_INVOCATION_DEPTH.remove();
        } else {
            DUAL_WIELD_RENDER_INVOCATION_DEPTH.set(Integer.valueOf(invocationDepth - 1));
        }
    }
}
