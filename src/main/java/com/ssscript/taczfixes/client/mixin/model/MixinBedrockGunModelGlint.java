package com.ssscript.taczfixes.client.mixin.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.GunGlintContext;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedrockGunModel.class)
public abstract class MixinBedrockGunModelGlint {
    private static final String RENDER =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V";

    @Inject(method = RENDER, at = @At("HEAD"), remap = false)
    private void taczfixes$beginGlint(PoseStack pose, ItemStack gunStack,
                                      ItemDisplayContext context, RenderType renderType,
                                      int light, int overlay, float red, float green, float blue,
                                      float alpha, MultiBufferSource bufferSource, CallbackInfo ci) {
        GunGlintContext.begin(gunStack);
    }

    @Inject(method = RENDER, at = @At("TAIL"), remap = false)
    private void taczfixes$renderGlintPass(PoseStack pose, ItemStack gunStack,
                                           ItemDisplayContext context, RenderType renderType,
                                           int light, int overlay, float red, float green, float blue,
                                           float alpha, MultiBufferSource bufferSource, CallbackInfo ci) {
        boolean active = GunGlintContext.isActive();
        GunGlintContext.end();
        if (!active || GunGlintContext.isGlintPass() || GunGlintContext.isSuppressed()) {
            return;
        }
        if (bufferSource instanceof MultiBufferSource.BufferSource buffers) {
            buffers.endBatch();
        }
        MultiBufferSource.BufferSource glintBuffer =
                MultiBufferSource.immediate(new BufferBuilder(1536));
        GunGlintContext.begin(gunStack);
        GunGlintContext.setGlintPass(true);
        try {
            ((BedrockGunModel) (Object) this).render(pose, gunStack, context,
                    RenderType.entityGlint(), light, overlay, red, green, blue, alpha, glintBuffer);
            glintBuffer.endBatch();
        } finally {
            GunGlintContext.setGlintPass(false);
            GunGlintContext.end();
        }
    }
}
