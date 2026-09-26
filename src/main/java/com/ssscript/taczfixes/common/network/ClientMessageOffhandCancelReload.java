package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ClientMessageOffhandCancelReload {
    private final UUID stackId;
    private final int reloadRequestId;

    public ClientMessageOffhandCancelReload(UUID stackId, int reloadRequestId) {
        this.stackId = stackId;
        this.reloadRequestId = reloadRequestId;
    }

    public static void encode(ClientMessageOffhandCancelReload packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
        buffer.writeVarInt(packet.reloadRequestId);
    }

    public static ClientMessageOffhandCancelReload decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandCancelReload(buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(ClientMessageOffhandCancelReload packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShooterManager.cancelReload(player, packet.stackId, packet.reloadRequestId);
            }
        });
        context.setPacketHandled(true);
    }
}
