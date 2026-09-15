package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.render.DualThirdPersonRenderer;
import com.ssscript.taczfixes.common.compat.YsmCompatibilityDiagnostics;
import com.ssscript.taczfixes.common.compat.YsmDualWieldCompat;
import com.ssscript.taczfixes.common.compat.YsmHeldLayerRenderState;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
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

@Pseudo
@Mixin(targets = {"com.elfmcys.yesstevemodel.o000Oo0OO0O00Oo0OOoOoooO"}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/compat/MixinYsmItemInHandLayer.class */
public abstract class MixinYsmItemInHandLayer {

    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/elfmcys/yesstevemodel/oo0OooOO0oOoOoOoo00oO000;FFFFFF)V"}, at = {@At("HEAD")}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$captureYsmEntityBase(PoseStack poseStack, MultiBufferSource buffer, int packedLight, @Coerce Object animatableRenderer, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callbackInfo) throws ReflectiveOperationException, IllegalArgumentException {
        YsmHeldLayerRenderState.prepareForEntry();
        YsmCompatibilityDiagnostics.markHeldLayerEntry();
        LivingEntity entity = YsmDualWieldCompat.mirrorFinalPose(animatableRenderer);
        if (entity != null) {
            YsmHeldLayerRenderState.begin(entity);
        }
    }

    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/elfmcys/yesstevemodel/oo0OooOO0oOoOoOoo00oO000;FFFFFF)V"}, at = {@At("RETURN")}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$clearYsmEntityBase(PoseStack poseStack, MultiBufferSource buffer, int packedLight, @Coerce Object animatableRenderer, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callbackInfo) {
        YsmHeldLayerRenderState.end();
    }

    @Redirect(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;m_269530_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$renderYsmHeldGun(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        YsmCompatibilityDiagnostics.markPrimaryHelperCall();
        if (leftHand && taczDualWield$isDualOffhand(entity, stack)) {
            if (!YsmHeldLayerRenderState.isOffhandRendered(entity) && taczDualWield$renderOffhand(entity, stack, poseStack, buffer, packedLight)) {
                YsmHeldLayerRenderState.markOffhandRendered(entity);
            }
            return;
        }
        renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, buffer, packedLight);
    }

    @Inject(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At(value = "INVOKE", target = "Lcom/elfmcys/yesstevemodel/o000Oo0OO0O00Oo0OOoOoooO;Oo0Oo0o00O00Oo0OOoOOoooo(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/elfmcys/yesstevemodel/OOOO0O0O000O000000oOOO0o;)Z", shift = At.Shift.AFTER, remap = false)}, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$renderYsmOffhandAfterArmLocator(@Coerce Object model, LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) {
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

    @Redirect(method = {"Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemDisplayContext;ZLnet/minecraft/client/renderer/MultiBufferSource;ILjava/util/List;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;m_269530_(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$renderYsmExtraOffhandGun(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (leftHand && taczDualWield$isDualOffhand(entity, stack)) {
            YsmCompatibilityDiagnostics.markExtraLocatorSuppressed();
            return;
        }
        renderer.renderItem(entity, stack, displayContext, leftHand, poseStack, buffer, packedLight);
    }

    private static boolean taczDualWield$renderOffhand(LivingEntity entity, ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        return DualThirdPersonRenderer.renderOffhand(entity, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    private static boolean taczDualWield$isDualOffhand(LivingEntity entity, ItemStack stack) {
        return DualThirdPersonRenderer.isDualMode(entity) && taczDualWield$isSameStack(stack, entity.getOffhandItem());
    }

    private static boolean taczDualWield$isSameStack(ItemStack first, ItemStack second) {
        return first == second || !(first.isEmpty() || second.isEmpty() || !ItemStack.isSameItemSameTags(first, second));
    }
}
