package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.event.FirstPersonRenderGunEvent", remap = false)
public class MixinFirstPersonRenderGunEvent {
    @Inject(method = "applyFirstPersonPositioningTransform", at = @At("HEAD"), remap = false)
    private static void taczfixes$captureAimProgress(PoseStack poseStack, BedrockGunModel model, ItemStack stack, float aimingProgress, float refitScreenOpeningProgress, CallbackInfo ci) {
        ScopeSwitchState.aimingProgressValue = aimingProgress;
    }
}