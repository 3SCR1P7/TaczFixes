package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ClientMessageOffhandFireSelect {
    private final UUID stackId;
    private final int requestId;

    public ClientMessageOffhandFireSelect(UUID stackId, int requestId) {
        this.stackId = stackId;
        this.requestId = requestId;
    }

    public static void encode(ClientMessageOffhandFireSelect packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
        buffer.writeVarInt(packet.requestId);
    }

    public static ClientMessageOffhandFireSelect decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandFireSelect(buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(ClientMessageOffhandFireSelect packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShooterManager.fireSelect(player, packet.stackId, packet.requestId);
            }
        });
        context.setPacketHandled(true);
    }
}
