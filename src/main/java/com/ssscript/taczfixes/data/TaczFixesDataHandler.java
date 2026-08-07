package com.ssscript.taczfixes.data;

import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TaczFixesDataHandler {
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TaczFixesDataReloadListener());
        event.addListener(new AttachmentLimbFactorReloadListener());
    }
}
