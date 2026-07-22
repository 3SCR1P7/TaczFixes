package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerShoot", remap = false)
public class MixinLocalPlayerShoot {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "shoot", at = @At("HEAD"))
    private void taczfixes$interruptSprintOnShoot(CallbackInfoReturnable<?> cir) {
        if (Config.FIRE_INTERRUPT_SPRINT.get() && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    @Inject(method = "chargeShoot", at = @At("HEAD"))
    private void taczfixes$interruptSprintOnCharge(boolean charging, CallbackInfoReturnable<Boolean> cir) {
        if (charging && Config.FIRE_INTERRUPT_SPRINT.get() && player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
