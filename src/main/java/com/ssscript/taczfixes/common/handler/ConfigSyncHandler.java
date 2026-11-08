package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.ConfigSync;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageSyncedConfig;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Sends the server-authoritative config snapshot to players: on login, and once after a server-side
 * config reload (the reload fires one event per spec file, so the broadcast is deferred to the next
 * server tick and happens only once for the whole reload).
 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID)
public final class ConfigSyncHandler {
    private static volatile boolean broadcastPending;

    private ConfigSyncHandler() {
    }

    /** Called by the mod-bus config reload listener. */
    public static void markConfigReloaded() {
        broadcastPending = true;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ServerMessageSyncedConfig(ConfigSync.snapshot()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !broadcastPending) {
            return;
        }
        broadcastPending = false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<ConfigSync.Entry> entries = ConfigSync.snapshot();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ServerMessageSyncedConfig(entries));
        }
    }
}
