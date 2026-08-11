package com.ssscript.taczfixes.mixin;

import com.ssscript.taczfixes.compat.ArcanaThermalState;
import group.taczexpands.dist.binq9IpL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "group/taczexpands/dist/binq9IpL", remap = false)
public class MixinArcanaScopeState {

    @Inject(method = "SYqi1im0", at = @At("HEAD"))
    private void taczfixes$captureScopeViewActive(boolean active, CallbackInfo ci) {
        boolean next = false;
        if (active) {
            next = ((binq9IpL) (Object) this).PtqK81kG(true);
        }
        ArcanaThermalState.scopeViewActive = next;
    }
}
