package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.PlayerInputState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** 玩家退出时清除其同步的按键状态。 */
public class InputStateCleanHandler {

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerInputState.remove(player.getUUID());
        }
    }
}
