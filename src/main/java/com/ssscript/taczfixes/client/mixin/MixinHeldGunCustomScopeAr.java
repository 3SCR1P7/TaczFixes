package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.CustomScopeArPolicy;
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
public abstract class MixinHeldGunCustomScopeAr {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("HEAD"), remap = false)
    private void taczfixes$beginCustomScopePolicy(PoseStack pose, ItemStack gun,
            ItemDisplayContext display, RenderType type, int light, int overlay,
            float red, float green, float blue, float alpha, MultiBufferSource.BufferSource buffers,
            CallbackInfo ci) {
        CustomScopeArPolicy.begin(gun, display);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFFLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("RETURN"), remap = false)
    private void taczfixes$endCustomScopePolicy(PoseStack pose, ItemStack gun,
            ItemDisplayContext display, RenderType type, int light, int overlay,
            float red, float green, float blue, float alpha, MultiBufferSource.BufferSource buffers,
            CallbackInfo ci) {
        CustomScopeArPolicy.end();
    }
}
