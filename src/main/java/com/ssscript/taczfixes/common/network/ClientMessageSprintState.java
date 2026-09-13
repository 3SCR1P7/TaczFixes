package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.StaminaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端→服务端: 本地玩家疾跑状态(避免服务端疾跑标记不同步导致不消耗耐力)。 */
public class ClientMessageSprintState {

    private final boolean sprinting;

    public ClientMessageSprintState(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public static void encode(ClientMessageSprintState message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.sprinting);
    }

    public static ClientMessageSprintState decode(FriendlyByteBuf buf) {
        return new ClientMessageSprintState(buf.readBoolean());
    }

    public static void handle(ClientMessageSprintState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            StaminaState.setClientSprinting(player.getUUID(), message.sprinting);
        });
        context.setPacketHandled(true);
    }
}
