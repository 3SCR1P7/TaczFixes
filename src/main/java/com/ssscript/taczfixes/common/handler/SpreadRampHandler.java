package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.SpreadState;
import net.neoforged.bus.api.SubscribeEvent;

public class SpreadRampHandler {
    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (true) {
            SpreadState.tick();
        }
    }
}
