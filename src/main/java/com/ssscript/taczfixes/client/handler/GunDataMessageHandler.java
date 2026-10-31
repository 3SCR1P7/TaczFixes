package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.util.CenterMessage;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class GunDataMessageHandler {
    @SubscribeEvent
    public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (true) {
            CenterMessage.tick();
        }
    }

    @SubscribeEvent
    public void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        CenterMessage.render(event.getGuiGraphics());
    }
}
