package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 副手枪械近战请求。 */
public final class ClientMessageOffhandMelee {
    private final UUID stackId;

    public ClientMessageOffhandMelee(UUID stackId) {
        this.stackId = stackId;
    }

    public static void encode(ClientMessageOffhandMelee packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
    }

    public static ClientMessageOffhandMelee decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandMelee(buffer.readUUID());
    }

    public static void handle(ClientMessageOffhandMelee packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShooterManager.melee(player, packet.stackId);
            }
        });
        context.setPacketHandled(true);
    }
}
