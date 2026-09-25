package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.render.DualFirstPersonRenderer;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {DualFirstPersonRenderer.class}, remap = false)
public abstract class MixinDualFirstPersonRendererParticles {
    @WrapOperation(method = {"renderOffhand"}, at = {@At(value = "INVOKE", target = "Lcom/ssscript/taczfixes/common/util/DualWieldEligibility;getClientLeftGunXOffset()D", remap = false)}, remap = false)
    private static double dualWield$applyPerGunLeftOffset(Operation<Double> original, @Local(argsOnly = true) ItemStack stack) {
        return DualWieldOverrides.leftOffset(stack, original.call().doubleValue());
    }

    @Inject(method = {"renderOffhand"}, at = {@At("HEAD")}, remap = false)
    private static void dualWield$captureOffhandParticleBasePose(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick, CallbackInfoReturnable<Boolean> callback) {
        if (!DualWieldClient.isDualMode(player)) {
            return;
        }
        MuzzleParticleManager.captureBasePose(InteractionHand.OFF_HAND, poseStack.last().pose());
    }

    @Inject(method = {"renderOffhand"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V", shift = At.Shift.BEFORE, remap = false)}, remap = false)
    private static void dualWield$spawnOffhandMuzzleParticles(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick, CallbackInfoReturnable<Boolean> callback) {
        if (!DualWieldClient.isDualMode(player)) {
            return;
        }
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        BedrockGunModel model = display == null ? null : display.getGunModel();
        if (model == null) {
            return;
        }
        boolean pushed = com.ssscript.taczfixes.client.util.BlockingModelTransform.apply(
                poseStack, model, stack, player, true);
        MuzzleParticleManager.spawnAndBind(FirstPersonRenderHandler.getParticleSystem(), poseStack, model, InteractionHand.OFF_HAND, null);
        if (pushed) {
            poseStack.popPose();
        }
    }

    @Inject(method = {"renderOffhand"}, at = {@At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V", shift = At.Shift.AFTER, remap = false)}, remap = false)
    private static void dualWield$renderOffhandMuzzleParticles(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick, CallbackInfoReturnable<Boolean> callback) {
        if (!DualWieldClient.isDualMode(player)) {
            return;
        }
        Matrix4f basePose = MuzzleParticleManager.getBasePose(InteractionHand.OFF_HAND);
        if (basePose == null) {
            return;
        }
        // 相对变换在 spawnAndBind 时已包含 blocking; 这里不能再叠加, 否则枪火会偏移
        poseStack.pushPose();
        try {
            poseStack.last().pose().set(basePose);
            FirstPersonRenderHandler.renderParticlesNow(InteractionHand.OFF_HAND, poseStack, bufferSource, light, partialTick);
        } finally {
            poseStack.popPose();
        }
    }
}
