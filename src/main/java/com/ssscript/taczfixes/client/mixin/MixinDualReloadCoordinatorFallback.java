package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualReloadCoordinator;
import com.ssscript.taczfixes.client.render.DualReloadFallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {DualReloadCoordinator.class}, remap = false)
public abstract class MixinDualReloadCoordinatorFallback {
    @Inject(method = {"handleManualReload"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$reloadOffhandWhenMainCannotReload(LocalPlayer player, CallbackInfoReturnable<Boolean> callback) {
        boolean mainReloadable = DualReloadFallback.isReloadable(player, InteractionHand.MAIN_HAND);
        boolean offhandReloadable = DualReloadFallback.isReloadable(player, InteractionHand.OFF_HAND);
        if (!mainReloadable && offhandReloadable) {
            DualReloadFallback.reloadHand(player, InteractionHand.OFF_HAND, false);
            callback.setReturnValue(Boolean.TRUE);
        }
    }

    @Inject(method = {"handleAutoReload"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$autoReloadSingleReloadableHand(LocalPlayer player, CallbackInfo callback) {
        boolean mainReloadable = DualReloadFallback.isReloadable(player, InteractionHand.MAIN_HAND);
        boolean offhandReloadable = DualReloadFallback.isReloadable(player, InteractionHand.OFF_HAND);
        if (mainReloadable == offhandReloadable) {
            return;
        }
        DualReloadFallback.reloadHand(player, mainReloadable ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        callback.cancel();
    }
}
