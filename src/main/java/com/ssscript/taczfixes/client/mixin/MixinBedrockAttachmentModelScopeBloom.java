package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.PbrBloom;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BedrockAttachmentModel.class, remap = false)
public abstract class MixinBedrockAttachmentModelScopeBloom {
    @Unique
    private boolean taczfixes$scopeBloomPushed;

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$pushScopeBloom(CallbackInfo callback) {
        this.taczfixes$scopeBloomPushed = ((BedrockAttachmentModel) (Object) this).isScope();
        if (this.taczfixes$scopeBloomPushed) {
            PbrRenderer.pushScopeAttachment();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFLnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("TAIL"), remap = false)
    private void taczfixes$popScopeBloom(CallbackInfo callback) {
        if (this.taczfixes$scopeBloomPushed) {
            this.taczfixes$scopeBloomPushed = false;
            PbrRenderer.popScopeAttachment();
            PbrBloom.captureScopeMask();
        }
    }
}
