package com.example.taczfixes.mixin;

import com.example.taczfixes.SpreadState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinShootOnceIncrement {
    @Inject(method = "shootOnce", at = @At("HEAD"), remap = false)
    private void taczfixes$onShootOnce(boolean needConsumeAmmo, CallbackInfo ci) {
        SpreadState.onShot();
    }
}
