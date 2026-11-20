package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端: 枪械 Lua 逻辑机脚本发布的自定义 HUD 参数。
 * 只同步主手枪械, 参数按快捷栏槽位缓存, 供 ApricityUI HTML HUD 读取。
 */
public class ServerMessageHudParam {

    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 512;
    private static final int MAX_ENTRIES = 64;

    private final int slot;
    private final String gunId;
    private final boolean clear;
    private final Map<String, String> values;
    private final Set<String> removed;

    public ServerMessageHudParam(int slot, String gunId, boolean clear,
                                 Map<String, String> values, Set<String> removed) {
        this.slot = slot;
        this.gunId = gunId == null ? "" : gunId;
        this.clear = clear;
        this.values = values == null ? Map.of() : values;
        this.removed = removed == null ? Set.of() : removed;
    }

    public static void encode(ServerMessageHudParam msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
        buf.writeUtf(msg.gunId, 128);
        buf.writeBoolean(msg.clear);
        buf.writeVarInt(Math.min(msg.values.size(), MAX_ENTRIES));
        int written = 0;
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            if (written++ >= MAX_ENTRIES) {
                break;
            }
            buf.writeUtf(entry.getKey(), MAX_KEY_LENGTH);
            buf.writeUtf(entry.getValue() == null ? "" : entry.getValue(), MAX_VALUE_LENGTH);
        }
        buf.writeVarInt(Math.min(msg.removed.size(), MAX_ENTRIES));
        written = 0;
        for (String key : msg.removed) {
            if (written++ >= MAX_ENTRIES) {
                break;
            }
            buf.writeUtf(key, MAX_KEY_LENGTH);
        }
    }

    public static ServerMessageHudParam decode(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        String gunId = buf.readUtf(128);
        boolean clear = buf.readBoolean();
        int valueCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < valueCount; i++) {
            values.put(buf.readUtf(MAX_KEY_LENGTH), buf.readUtf(MAX_VALUE_LENGTH));
        }
        int removedCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Set<String> removed = new HashSet<>();
        for (int i = 0; i < removedCount; i++) {
            removed.add(buf.readUtf(MAX_KEY_LENGTH));
        }
        return new ServerMessageHudParam(slot, gunId, clear, values, removed);
    }

    public static void handle(ServerMessageHudParam msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.hud.HudParamStore.apply(
                        msg.slot, msg.gunId, msg.clear, msg.values, msg.removed)));
        context.setPacketHandled(true);
    }
}
