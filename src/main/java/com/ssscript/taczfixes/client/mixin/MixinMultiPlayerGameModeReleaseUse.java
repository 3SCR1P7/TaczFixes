package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla releases the currently used item every tick when the right-click use key is not held.
 * Offhand items in this mod are used with the left attack key, so that release would end the
 * offhand use before it completes. Keep the offhand use alive while the left key is held.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameModeReleaseUse {
    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    private void taczfixes$keepOffhandUse(Player player, CallbackInfo callback) {
        if (player instanceof LocalPlayer localPlayer && DualWieldClient.shouldKeepOffhandUse(localPlayer)) {
            callback.cancel();
        }
    }
}
