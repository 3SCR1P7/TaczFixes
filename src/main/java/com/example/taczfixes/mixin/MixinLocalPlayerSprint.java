package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerSprint", remap = false)
public class MixinLocalPlayerSprint {
    @Shadow(remap = false)
    private LocalPlayer player;

    @Inject(method = "getProcessedSprintStatus", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$onGetProcessedSprintStatus(boolean originalSprintStatus, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.ADS_INTERRUPT_SPRINT.get()) return;
        if (player instanceof IClientPlayerGunOperator operator && operator.isAim()) {
            cir.setReturnValue(false);
        }
    }
}
