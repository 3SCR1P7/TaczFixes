package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.CenterMessage;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GunDataMessageHandler {
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CenterMessage.tick();
        }
    }

    @SubscribeEvent
    public void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        CenterMessage.render(event.getGuiGraphics());
    }
}
