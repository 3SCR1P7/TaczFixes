package com.ssscript.taczfixes.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.render.DualThirdPersonRenderer;
import com.ssscript.taczfixes.common.compat.TouhouMaidDualWieldAnimation;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld"}, remap = false)
public abstract class MixinTouhouMaidHeldGun {
    @Redirect(method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V"}, at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = ServerMessageOffhandActionResult.ACTION_RELOAD), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private boolean taczDualWield$allowMissingPrimaryLeftLocator(List<?> bones, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Mob maid, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        return bones.isEmpty() && !DualThirdPersonRenderer.isDualMode(maid);
    }

    @Inject(method = {"renderArmWithItem(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/item/ItemStack;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/animated/ILocationModel;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$renderMaidOffhandGun(Mob maid, ItemStack stack, ILocationModel model, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) {
        if (arm != HumanoidArm.LEFT || !DualThirdPersonRenderer.isDualMode(maid)) {
            return;
        }
        List<? extends ILocationBone> leftBones = model.leftHandBones();
        boolean rendered = renderAtLocator(maid, stack, leftBones, poseStack, buffer, packedLight);
        if (!rendered && (leftBones == null || leftBones.isEmpty())) {
            rendered = renderAtMirroredRightLocator(maid, stack, model.rightHandBones(), poseStack, buffer, packedLight);
        }
        if (rendered) {
            callbackInfo.cancel();
        }
    }

    private static boolean renderAtLocator(Mob maid, ItemStack stack, List<? extends ILocationBone> bones, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (bones == null || bones.isEmpty()) {
            return false;
        }
        poseStack.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(poseStack, bones)) {
                return false;
            }
            poseStack.translate(0.0d, -0.0625d, -0.1d);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            boolean zRenderOffhand = DualThirdPersonRenderer.renderOffhand(maid, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return zRenderOffhand;
        } finally {
            poseStack.popPose();
        }
    }

    private static boolean renderAtMirroredRightLocator(Mob maid, ItemStack stack, List<? extends ILocationBone> bones, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (bones == null || bones.isEmpty()) {
            return false;
        }
        poseStack.pushPose();
        try {
            poseStack.scale(-1.0f, 1.0f, 1.0f);
            if (RenderUtils.prepMatrixForLocator(poseStack, bones)) {
                return false;
            }
            poseStack.scale(-1.0f, 1.0f, 1.0f);
            TouhouMaidDualWieldAnimation.applyFallbackOffhandLowReady(maid, poseStack);
            poseStack.translate(0.0d, -0.0625d, -0.1d);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            boolean zRenderOffhand = DualThirdPersonRenderer.renderOffhand(maid, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            return zRenderOffhand;
        } finally {
            poseStack.popPose();
        }
    }
}
