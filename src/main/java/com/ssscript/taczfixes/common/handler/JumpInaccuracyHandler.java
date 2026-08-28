package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.util.JumpInaccuracyState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class JumpInaccuracyHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            JumpInaccuracyState.tick();
        }
    }
}
