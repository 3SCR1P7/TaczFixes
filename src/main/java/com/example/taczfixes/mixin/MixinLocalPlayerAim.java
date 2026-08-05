package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.PeekState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerAim", remap = false)
public class MixinLocalPlayerAim {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "aim", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$onAim(boolean aiming, CallbackInfo ci) {
        if (aiming) {
            if (Config.ADS_INTERRUPT_SPRINT.get() && player.isSprinting()) {
                player.setSprinting(false);
            }
            return;
        }

        if (!Config.AUTO_AIM_WHEN_PEEKING.get()) return;
        if (PeekState.isPeeking) {
            ci.cancel();
        }
    }
}
