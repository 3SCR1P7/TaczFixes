package com.ssscript.taczfixes.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.compat.gun.common.GunClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ssscript.taczfixes.client.render.DualThirdPersonRenderer;
import com.ssscript.taczfixes.common.compat.TouhouMaidDualWieldAnimation;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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
@Mixin(targets = {"com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidHeldItem"}, remap = false)
public abstract class MixinTouhouMaidBedrockHeldGun extends RenderLayer<Mob, BedrockModel<Mob>> {
    protected MixinTouhouMaidBedrockHeldGun(RenderLayerParent<Mob, BedrockModel<Mob>> renderer) {
        super(renderer);
    }

    @Redirect(method = {"render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V"}, at = @At(value = "INVOKE", target = "Lcom/github/tartaricacid/touhoulittlemaid/client/model/bedrock/BedrockModel;hasLeftArm()Z"), require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private boolean taczDualWield$allowMissingNamedLeftArm(BedrockModel<Mob> model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Mob maid, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        return model.hasLeftArm() || DualThirdPersonRenderer.isDualMode(maid);
    }

    @Inject(method = {"renderArmWithItem(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at = {@At("HEAD")}, cancellable = true, require = ServerMessageOffhandActionResult.ACTION_SHOOT, remap = false)
    private void taczDualWield$renderMaidBedrockOffhand(Mob maid, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo callbackInfo) {
        if (arm != HumanoidArm.LEFT || !DualThirdPersonRenderer.isDualMode(maid)) {
            return;
        }
        BedrockModel<Mob> model = getParentModel();
        poseStack.pushPose();
        try {
            boolean mirrorRightArm = !model.hasLeftArm() && model.hasRightArm();
            HumanoidArm sourceArm = mirrorRightArm ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            if (mirrorRightArm) {
                poseStack.scale(-1.0f, 1.0f, 1.0f);
            }
            model.translateToHand(sourceArm, poseStack);
            boolean hasPositioningBone = model.hasArmPositioningModel(sourceArm);
            if (hasPositioningBone) {
                model.translateToPositioningHand(sourceArm, poseStack);
            }
            if (mirrorRightArm) {
                poseStack.scale(-1.0f, 1.0f, 1.0f);
                TouhouMaidDualWieldAnimation.applyFallbackOffhandLowReady(maid, poseStack);
            }
            if (hasPositioningBone) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.translate(0.0d, 0.125d, -0.0625d);
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                poseStack.translate(-0.0625d, 0.125d, -0.525d);
            }
            GunClientUtil.addItemTranslate(poseStack, stack, true);
            if (DualThirdPersonRenderer.renderOffhand(maid, stack, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY)) {
                callbackInfo.cancel();
            }
        } finally {
            poseStack.popPose();
        }
    }
}
