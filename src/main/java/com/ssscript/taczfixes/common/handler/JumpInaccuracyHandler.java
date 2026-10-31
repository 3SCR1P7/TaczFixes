package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.JumpInaccuracyState;
import net.neoforged.bus.api.SubscribeEvent;

public class JumpInaccuracyHandler {
    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (true) {
            JumpInaccuracyState.tick();
        }
    }
}
