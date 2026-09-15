package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.input.AimKey;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {AimKey.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/mixin/MixinAimKey.class */
public abstract class MixinAimKey {
    @Inject(method = {"onAimPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$useRightClickForMainGun(InputEvent.MouseButton.Post event, CallbackInfo callback) {
        if (DualWieldClient.isDualMode(Minecraft.getInstance().player)) {
            callback.cancel();
        }
    }

    @Inject(method = {"onAimHoldingPreInput"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$stopHoldAim(TickEvent.ClientTickEvent event, CallbackInfo callback) {
        if (DualWieldClient.isDualMode(Minecraft.getInstance().player)) {
            callback.cancel();
        }
    }

    @Inject(method = {"onAimControllerPress"}, at = {@At("HEAD")}, cancellable = true)
    private static void dualWield$routeControllerAim(boolean isPress, CallbackInfoReturnable<Boolean> callback) {
        if (DualWieldClient.isDualMode(Minecraft.getInstance().player)) {
            DualWieldClient.setControllerAimDown(isPress);
            callback.setReturnValue(true);
        }
    }
}
