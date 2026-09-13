package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.PlayerInputState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** 客户端→服务端: 同步本地玩家当前按下的按键集(供服务端 Lua getInput() 使用)。 */
public class ClientMessageInputSync {

    private static final int MAX_KEYS = 128;

    private final List<String> keys;

    public ClientMessageInputSync(List<String> keys) {
        this.keys = keys;
    }

    public static void encode(ClientMessageInputSync message, FriendlyByteBuf buf) {
        List<String> keys = message.keys == null ? new ArrayList<>() : message.keys;
        buf.writeVarInt(Math.min(keys.size(), MAX_KEYS));
        for (int i = 0; i < keys.size() && i < MAX_KEYS; i++) {
            buf.writeUtf(keys.get(i) == null ? "" : keys.get(i), 64);
        }
    }

    public static ClientMessageInputSync decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0) count = 0;
        if (count > MAX_KEYS) count = MAX_KEYS;
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(buf.readUtf(64));
        }
        return new ClientMessageInputSync(keys);
    }

    public static void handle(ClientMessageInputSync message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageInputSync message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        Set<String> set = new HashSet<>();
        if (message.keys != null) {
            for (String key : message.keys) {
                if (key == null || !(key.startsWith("key.keyboard.") || key.startsWith("key.mouse."))) continue;
                if (set.size() >= MAX_KEYS) break;
                set.add(key);
            }
        }
        PlayerInputState.set(player.getUUID(), set);
    }
}
