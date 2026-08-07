package com.ssscript.taczfixes.handler;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.SpreadState;
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
