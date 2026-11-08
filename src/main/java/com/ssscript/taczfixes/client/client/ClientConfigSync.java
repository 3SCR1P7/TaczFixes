package com.ssscript.taczfixes.client.client;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.ConfigSync;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Restores the local (client) config values once the server-authoritative overrides are no longer valid. */
@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = {Dist.CLIENT})
public final class ClientConfigSync {
    private ClientConfigSync() {
    }

    /**
     * Applies the server snapshot. In single player the integrated server shares the same COMMON
     * config instances, so the client already sees the server values and must not override them.
     */
    public static void applyServerValues(List<ConfigSync.Entry> entries) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        ConfigSync.applyServerValues(entries);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ConfigSync.restoreClientLocalValues();
    }
}
