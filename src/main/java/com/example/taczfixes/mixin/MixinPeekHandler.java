package com.example.taczfixes.mixin;

import com.example.taczfixes.Config;
import com.example.taczfixes.PeekState;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.mods.gd656peek.client.PeekHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PeekHandler.class)
public class MixinPeekHandler {
    @Shadow(remap = false)
    private static boolean leftPressed;

    @Shadow(remap = false)
    private static boolean rightPressed;

    @Unique
    private static boolean taczfixes$wasPeeking = false;

    @Inject(method = "onClientTick", at = @At("TAIL"), remap = false)
    private static void taczfixes$onPeekTick(net.minecraftforge.event.TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        boolean peeking = leftPressed || rightPressed;
        PeekState.isPeeking = peeking;

        if (Config.AUTO_AIM_WHEN_PEEKING.get()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || player.isSpectator()) return;

            if (player instanceof IClientPlayerGunOperator operator && IGun.mainHandHoldGun(player)) {
                boolean aiming = operator.isAim();
                if (peeking && !aiming) {
                    operator.aim(true);
                } else if (!peeking && aiming && taczfixes$wasPeeking) {
                    operator.aim(false);
                }
            }
        }

        taczfixes$wasPeeking = peeking;
    }
}
