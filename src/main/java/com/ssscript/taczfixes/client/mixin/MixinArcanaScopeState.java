package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.compat.ArcanaThermalState;
import group.taczexpands.dist.WFkyOIm9;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "group/taczexpands/dist/WFkyOIm9", remap = false)
public class MixinArcanaScopeState {

    @Inject(method = "a0C67gtC", at = @At("HEAD"))
    private void taczfixes$captureScopeViewActive(boolean active, CallbackInfo ci) {
        boolean next = false;
        if (active) {
            next = ((WFkyOIm9) (Object) this).Mf4hhsG4(true);
        }
        ArcanaThermalState.scopeViewActive = next;
    }
}
