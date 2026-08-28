package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunAttachmentSlot.class)
public class MixinGunAttachmentSlotSelection {

    @Inject(method = "setSelected", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$suppressStandardSelection(boolean selected, CallbackInfo ci) {
        if (CustomSlotGuiState.get() != null) {
            ci.cancel();
        }
    }
}
