package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 常凪冻结期间兜底清理: 持续使用物品被中止、冲刺被取消、输入清零。 */
public class FreezeInputHandler {
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        FreezeClientState.pruneExpired();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !FreezeClientState.isFrozen()) {
            return;
        }
        player.input.leftImpulse = 0.0F;
        player.input.forwardImpulse = 0.0F;
        player.input.jumping = false;
        player.input.shiftKeyDown = false;
        player.setSprinting(false);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FreezeClientState.clear();
    }
}
