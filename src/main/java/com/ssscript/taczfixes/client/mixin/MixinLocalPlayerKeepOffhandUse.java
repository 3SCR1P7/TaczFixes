package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla stops using an item when the use key is not held. Offhand use is
 * started by the left attack key in dual wield, so keep the use state alive
 * while the attack key remains held.
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerKeepOffhandUse {
    @Inject(method = {"stopUsingItem", "stopUsingItem"}, at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void taczfixes$keepOffhandUse(CallbackInfo callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (DualWieldClient.shouldKeepOffhandUse(player)) {
            callback.cancel();
        }
    }
}
