package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.input.ReloadKey;
import com.ssscript.taczfixes.client.client.DualReloadCoordinator;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ReloadKey.class}, remap = false)
public abstract class MixinReloadKey {
    @Inject(method = {"onReloadPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$selectReloadHand(CallbackInfoReturnable<Boolean> callback) {
        if (DualReloadCoordinator.interceptKeyboardReload()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = {"onReloadControllerPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$selectControllerReloadHand(boolean isPress, CallbackInfoReturnable<Boolean> callback) {
        if (DualReloadCoordinator.interceptControllerReload(isPress)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = {"autoReload"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$replaceMainOnlyAutoReload(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event, CallbackInfo callback) {
        if (DualReloadCoordinator.shouldSuppressNativeAutoReload()) {
            callback.cancel();
        }
    }
}
