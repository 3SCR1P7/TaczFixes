package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.PlayerInputState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 玩家退出时清除其同步的按键状态。 */
public class InputStateCleanHandler {

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerInputState.remove(player.getUUID());
        }
    }
}
