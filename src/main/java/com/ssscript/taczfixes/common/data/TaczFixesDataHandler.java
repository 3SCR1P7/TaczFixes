package com.ssscript.taczfixes.common.data;

import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class TaczFixesDataHandler {
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TaczFixesDataReloadListener());
        event.addListener(new AttachmentTaczFixesReloadListener());
        event.addListener(new AdapterNativeDataReloadListener());
    }
}
