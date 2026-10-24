package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.handler.GunSmithTableAutoCraftHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageAutoCraftStateRequest {
    private final int containerId;

    public ClientMessageAutoCraftStateRequest(int containerId) {
        this.containerId = containerId;
    }

    public static void encode(ClientMessageAutoCraftStateRequest message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.containerId);
    }

    public static ClientMessageAutoCraftStateRequest decode(FriendlyByteBuf buf) {
        return new ClientMessageAutoCraftStateRequest(buf.readVarInt());
    }

    public static void handle(ClientMessageAutoCraftStateRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                GunSmithTableAutoCraftHandler.query(player, message.containerId);
            }
        });
        context.setPacketHandled(true);
    }
}
