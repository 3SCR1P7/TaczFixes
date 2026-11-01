package com.ssscript.taczfixes.client.mixin.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.GunSlotGlintContext;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class MixinGunItemRendererWrapperSlotGlint {
    @Inject(method = "lambda$renderByItem$6", at = @At("HEAD"), remap = false)
    private static void taczfixes$beginSlotGlint(ItemDisplayContext context, PoseStack poseStack,
                                                  MultiBufferSource bufferSource, int light, int overlay,
                                                  ItemStack stack, GunDisplayInstance display, CallbackInfo ci) {
        GunSlotGlintContext.begin(stack);
    }

    @Inject(method = "lambda$renderByItem$6", at = @At("RETURN"), remap = false)
    private static void taczfixes$endSlotGlint(ItemDisplayContext context, PoseStack poseStack,
                                                MultiBufferSource bufferSource, int light, int overlay,
                                                ItemStack stack, GunDisplayInstance display, CallbackInfo ci) {
        GunSlotGlintContext.end();
    }
}
