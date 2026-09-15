package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.render.OffhandArmPoseResolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 双持时隐藏主手枪械模型的支撑手(左手), 使主手只保留右手手臂。
 */
@Mixin(targets = {"com.tacz.guns.client.renderer.item.GunItemRendererWrapper"}, remap = false)
public abstract class MixinMainHandSupportArm {

    @Inject(method = {"lambda$renderFirstPerson$5"}, at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/model/BedrockGunModel;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", shift = At.Shift.BEFORE, remap = false), require = 0, remap = false)
    private void dualWield$hideMainSupportHand(ItemStack stack, LocalPlayer player, float partialTick, PoseStack poseStack, ItemDisplayContext context, MultiBufferSource bufferSource, int light, GunDisplayInstance display, CallbackInfo callback) {
        if (player == null || !DualWieldClient.isDualMode(player) || DualRenderContext.getPhase() != DualRenderContext.HandPhase.MAIN) {
            return;
        }
        BedrockGunModel model = display == null ? null : display.getGunModel();
        if (model == null) {
            return;
        }
        for (String name : OffhandArmPoseResolver.resolveSupportArmAnimationNodes(model)) {
            BedrockPart part = model.getNode(name);
            if (part != null) {
                part.offsetY += 1000.0f;
            }
        }
    }
}
