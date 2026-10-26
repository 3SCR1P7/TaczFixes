package com.ssscript.taczfixes.client.mixin.compat;

import com.ssscript.taczfixes.client.client.ClientArGate;
import com.tacz.guns.compat.ar.ARCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ARCompat.class, remap = false)
public abstract class MixinARCompatGate {
    @Inject(method = "shouldAccelerate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$forceVanillaForCustomOptic(CallbackInfoReturnable<Boolean> callback) {
        if (ClientArGate.isForcedVanilla()) {
            callback.setReturnValue(false);
        }
    }
}
