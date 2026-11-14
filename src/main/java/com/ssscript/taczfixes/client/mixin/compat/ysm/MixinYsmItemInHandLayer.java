package com.ssscript.taczfixes.client.mixin.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.client.DualThirdPersonRenderer;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.compat.YsmHeldLayerRenderState;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * YSM 手持物品渲染层 (1.21.1 NeoForge 构建: com.elfmcys.yesstevemodel.o000oOO0000oOOOOo0O0oooO)。
 * <p>YSM 自身的层只负责把物品渲染到模型手持挂点上; 双持时由本 mixin 接管副手枪械的模型渲染,
 * 并在同一帧内记录状态, 避免同一副手物品被 YSM 的多个挂点重复绘制。
 */
@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.o000oOO0000oOOOOo0O0oooO"}, remap = false)
public abstract class MixinYsmItemInHandLayer {

    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/elfmcys/yesstevemodel/OOoOoO0o0o0o000OO0o0ooo0;FFFFFF)V"},
            at = {@At("HEAD")},
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$captureYsmEntityBase(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                    @Coerce Object animatableRenderer, float limbSwing,
                                                    float limbSwingAmount, float partialTick, float ageInTicks,
                                                    float netHeadYaw, float headPitch,
                                                    CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        YsmHeldLayerRenderState.prepareForEntry();
        YsmCompatibilityDiagnostics.markHeldLayerEntry();
        LivingEntity entity = YsmDualWieldCompat.mirrorFinalPose(animatableRenderer);
        if (entity != null) {
            YsmHeldLayerRenderState.begin(entity);
        }
    }

    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/elfmcys/yesstevemodel/OOoOoO0o0o0o000OO0o0ooo0;FFFFFF)V"},
            at = {@At("RETURN")},
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$clearYsmEntityBase(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                  @Coerce Object animatableRenderer, float limbSwing,
                                                  float limbSwingAmount, float partialTick, float ageInTicks,
                                                  float netHeadYaw, float headPitch, CallbackInfo callbackInfo) {
        YsmHeldLayerRenderState.end();
    }

    @Redirect(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$renderYsmHeldGun(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack,
                                                ItemDisplayContext displayContext, boolean leftHand,
                                                PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        YsmCompatibilityDiagnostics.markPrimaryHelperCall();
        if (leftHand && taczDualWield$isDualOffhand(entity, stack)) {
            if (YsmHeldLayerRenderState.isOffhandRendered(entity)) {
                return;
            }
            if (taczDualWield$renderOffhand(entity, stack, poseStack, buffer, packedLight)) {
                YsmHeldLayerRenderState.markOffhandRendered(entity);
                return;
            }
        }
        renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, buffer, packedLight);
    }

    @Inject(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lcom/elfmcys/yesstevemodel/o000oOO0000oOOOOo0O0oooO;oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/elfmcys/yesstevemodel/o0ooO0ooO00oo0o00Oo00000;)Z",
                    shift = At.Shift.AFTER,
                    remap = false)},
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$renderYsmOffhandAfterArmLocator(@Coerce Object model, LivingEntity entity,
                                                               ItemStack stack, ItemDisplayContext displayContext,
                                                               HumanoidArm arm, PoseStack poseStack,
                                                               MultiBufferSource buffer, int packedLight,
                                                               CallbackInfo callbackInfo) {
        if (arm != HumanoidArm.LEFT || !taczDualWield$isDualOffhand(entity, stack) || YsmHeldLayerRenderState.isOffhandRendered(entity)) {
            return;
        }
        poseStack.pushPose();
        try {
            poseStack.translate(0.0d, -0.0625d, -0.1d);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            if (taczDualWield$renderOffhand(entity, stack, poseStack, buffer, packedLight)) {
                YsmHeldLayerRenderState.markOffhandRendered(entity);
            }
        } finally {
            poseStack.popPose();
        }
    }

    @Redirect(
            method = {"oOo0OO0O0o000OO0O000oo0o(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemDisplayContext;ZLnet/minecraft/client/renderer/MultiBufferSource;ILjava/util/List;)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    remap = false),
            require = ServerMessageOffhandActionResult.ACTION_SHOOT,
            remap = false)
    private void taczDualWield$renderYsmExtraOffhandGun(ItemInHandRenderer renderer, LivingEntity entity,
                                                        ItemStack stack, ItemDisplayContext displayContext,
                                                        boolean leftHand, PoseStack poseStack,
                                                        MultiBufferSource buffer, int packedLight) {
        if (leftHand && taczDualWield$isDualOffhand(entity, stack) && IGun.getIGunOrNull(stack) != null) {
            YsmCompatibilityDiagnostics.markExtraLocatorSuppressed();
            return;
        }
        renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, buffer, packedLight);
    }

    private static boolean taczDualWield$renderOffhand(LivingEntity entity, ItemStack stack, PoseStack poseStack,
                                                       MultiBufferSource buffer, int packedLight) {
        return DualThirdPersonRenderer.renderOffhand(entity, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    private static boolean taczDualWield$isDualOffhand(LivingEntity entity, ItemStack stack) {
        return DualThirdPersonRenderer.isDualMode(entity) && taczDualWield$isSameStack(stack, entity.getOffhandItem());
    }

    private static boolean taczDualWield$isSameStack(ItemStack first, ItemStack second) {
        return first == second || !(first.isEmpty() || second.isEmpty() || !ItemStack.isSameItemSameComponents(first, second));
    }
}
