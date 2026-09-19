package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.render.DualFirstPersonRenderer;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.OffhandArmPoseResolver;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 双持第一人称: 隐藏副手枪械模型的持握手臂节点 (改用与主手一致的右手手臂呈现)。
 */
@Mixin(value = {DualFirstPersonRenderer.class}, remap = false)
public abstract class MixinDualFirstPersonRendererArm {

    @Inject(method = {"renderOffhand"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V", shift = At.Shift.BEFORE, remap = false), require = 0, remap = false)
    private static void dualWield$hideOffhandModelArm(LocalPlayer player, ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick, CallbackInfoReturnable<Boolean> callback) {
        if (player == null || DualRenderContext.getPhase() != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        GunDisplayInstance display = OffhandDisplayManager.getOrCreate(stack);
        BedrockGunModel model = display == null ? null : display.getGunModel();
        if (model == null || !model.getRenderHand()) {
            return;
        }
        DualWieldOverrides.HandPos handPos = DualRenderContext.offhandHandPos();
        boolean keepLeft = handPos.anchor() == DualWieldOverrides.ArmAnchor.LEFT
                || handPos.anchor() == DualWieldOverrides.ArmAnchor.BOTH;
        boolean keepRight = handPos.anchor() == DualWieldOverrides.ArmAnchor.RIGHT
                || handPos.anchor() == DualWieldOverrides.ArmAnchor.BOTH;
        java.util.Set<String> holdingNodes = OffhandArmPoseResolver.resolveHoldingArmAnimationNodes(model);
        if ((keepLeft && holdingNodes.contains("lefthandpos"))
                || (keepRight && holdingNodes.contains("righthandpos"))) {
            return;
        }
        for (String name : holdingNodes) {
            BedrockPart part = model.getNode(name);
            if (part != null) {
                part.offsetY += 1000.0f;
            }
        }
    }
}
