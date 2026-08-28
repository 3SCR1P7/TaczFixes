package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.SpreadState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpreadRampHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpreadState.tick();
        }
    }
}
