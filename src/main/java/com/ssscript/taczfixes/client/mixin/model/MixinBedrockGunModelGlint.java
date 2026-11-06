package com.ssscript.taczfixes.client.mixin.model;

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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, index = 11, remap = false)
    private MultiBufferSource taczfixes$wrapGlintSource(MultiBufferSource source) {
        return GunGlintContext.wrapSource(source);
    }

    @Inject(method = RENDER, at = @At("TAIL"), remap = false)
    private void taczfixes$endGlint(PoseStack pose, ItemStack gunStack,
                                    ItemDisplayContext context, RenderType renderType,
                                    int light, int overlay, float red, float green, float blue,
                                    float alpha, MultiBufferSource bufferSource, CallbackInfo ci) {
        GunGlintContext.end();
    }
}
