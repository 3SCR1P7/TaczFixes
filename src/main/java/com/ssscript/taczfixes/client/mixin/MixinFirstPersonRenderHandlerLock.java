package com.ssscript.taczfixes.client.mixin;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FirstPersonRenderHandler.class, remap = false)
public class MixinFirstPersonRenderHandlerLock {

    @Inject(method = "shouldLockVanilla(Lnet/minecraft/world/InteractionHand;)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void taczfixes$unlockOffhandItem(InteractionHand hand,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (hand != InteractionHand.OFF_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
                && IGun.getIGunOrNull(player.getMainHandItem()) != null
                && IGun.getIGunOrNull(player.getOffhandItem()) == null) {
            callback.setReturnValue(false);
        }
    }
}
