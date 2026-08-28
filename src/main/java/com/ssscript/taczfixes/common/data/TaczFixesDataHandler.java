package com.ssscript.taczfixes.common.data;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TaczFixesDataHandler {
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TaczFixesDataReloadListener());
        event.addListener(new AttachmentTaczFixesReloadListener());
    }
}
