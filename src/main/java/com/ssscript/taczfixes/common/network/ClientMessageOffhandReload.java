package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/network/packet/ClientMessageOffhandReload.class */
public final class ClientMessageOffhandReload {
    private final UUID stackId;
    private final int requestId;

    public ClientMessageOffhandReload(UUID stackId, int requestId) {
        this.stackId = stackId;
        this.requestId = requestId;
    }

    public static void encode(ClientMessageOffhandReload packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
        buffer.writeVarInt(packet.requestId);
    }

    public static ClientMessageOffhandReload decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandReload(buffer.readUUID(), buffer.readVarInt());
    }

    public static void handle(ClientMessageOffhandReload packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShooterManager.reload(player, packet.stackId, packet.requestId);
            }
        });
        context.setPacketHandled(true);
    }
}
