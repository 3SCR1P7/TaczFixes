package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.ssscript.taczfixes.client.render.DualRenderContext;
import com.ssscript.taczfixes.common.network.ServerMessageOffhandActionResult;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {BedrockAttachmentModel.class}, remap = false)
public abstract class MixinBedrockAttachmentModel {
    @Redirect(method = {"render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemDisplayContext;firstPerson()Z", remap = true), require = ServerMessageOffhandActionResult.ACTION_RELOAD)
    private boolean dualWield$renderPhysicalOffhandOptic(ItemDisplayContext transformType) {
        return (DualRenderContext.getPhase() == DualRenderContext.HandPhase.OFFHAND || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || transformType != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) ? false : true;
    }
}
