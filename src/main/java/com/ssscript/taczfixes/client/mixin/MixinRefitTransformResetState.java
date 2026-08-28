package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RefitTransform.class)
public class MixinRefitTransformResetState {

    @Inject(method = "changeRefitScreenView", at = @At("HEAD"), remap = false)
    private static void taczfixes$resetCustomSlotState(AttachmentType type, CallbackInfoReturnable<Boolean> cir) {
        CustomSlotGuiState.beginRefitViewTransition();
        CustomSlotGuiState.reset();
    }
}
