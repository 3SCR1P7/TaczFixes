package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.render.DualWieldClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {DualWieldClient.class}, remap = false)
public abstract class MixinDualWieldClientInspect {
    @Inject(method = {"inspectOffhand"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void dualWield$keepInspectOnMainHand(LocalPlayer player, CallbackInfo callback) {
        callback.cancel();
    }
}
