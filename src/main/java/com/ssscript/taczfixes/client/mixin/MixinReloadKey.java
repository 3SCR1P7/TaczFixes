package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.input.ReloadKey;
import com.ssscript.taczfixes.client.render.DualReloadCoordinator;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ReloadKey.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinReloadKey.class */
public abstract class MixinReloadKey {
    @Inject(method = {"onReloadPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$selectReloadHand(InputEvent.Key event, CallbackInfo callback) {
        if (DualReloadCoordinator.interceptKeyboardReload(event)) {
            callback.cancel();
        }
    }

    @Inject(method = {"onReloadControllerPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$selectControllerReloadHand(boolean isPress, CallbackInfoReturnable<Boolean> callback) {
        if (DualReloadCoordinator.interceptControllerReload(isPress)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = {"autoReload"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$replaceMainOnlyAutoReload(TickEvent.PlayerTickEvent event, CallbackInfo callback) {
        if (DualReloadCoordinator.shouldSuppressNativeAutoReload()) {
            callback.cancel();
        }
    }
}
