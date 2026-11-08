package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.config.ConfigSync;
import com.ssscript.taczfixes.common.util.DistExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

/** Server -> client: server-authoritative config entries (spec key, config path, JSON value). */
public final class ServerMessageSyncedConfig {
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_PATH_DEPTH = 16;
    private static final int MAX_SPEC_LENGTH = 64;
    private static final int MAX_PATH_PART_LENGTH = 128;
    private static final int MAX_JSON_LENGTH = 32767;

    private final List<ConfigSync.Entry> entries;

    public ServerMessageSyncedConfig(List<ConfigSync.Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static void encode(ServerMessageSyncedConfig message, FriendlyByteBuf buffer) {
        List<ConfigSync.Entry> entries = message.entries;
        buffer.writeVarInt(entries.size());
        for (ConfigSync.Entry entry : entries) {
            buffer.writeUtf(entry.spec(), MAX_SPEC_LENGTH);
            List<String> path = entry.path();
            buffer.writeVarInt(path.size());
            for (String part : path) {
                buffer.writeUtf(part, MAX_PATH_PART_LENGTH);
            }
            buffer.writeUtf(entry.json(), MAX_JSON_LENGTH);
        }
    }

    public static ServerMessageSyncedConfig decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid synced config entry count: " + size);
        }
        List<ConfigSync.Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String spec = buffer.readUtf(MAX_SPEC_LENGTH);
            int pathSize = buffer.readVarInt();
            if (pathSize <= 0 || pathSize > MAX_PATH_DEPTH) {
                throw new IllegalArgumentException("Invalid synced config path depth: " + pathSize);
            }
            List<String> path = new ArrayList<>(pathSize);
            for (int part = 0; part < pathSize; part++) {
                path.add(buffer.readUtf(MAX_PATH_PART_LENGTH));
            }
            String json = buffer.readUtf(MAX_JSON_LENGTH);
            entries.add(new ConfigSync.Entry(spec, List.copyOf(path), json));
        }
        return new ServerMessageSyncedConfig(entries);
    }

    public static void handle(ServerMessageSyncedConfig message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.client.ClientConfigSync.applyServerValues(message.entries)));
        context.setPacketHandled(true);
    }
}
