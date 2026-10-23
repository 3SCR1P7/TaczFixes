package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandItemCombat;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ClientMessageOffhandItemAttack {
    private final int targetId;

    public ClientMessageOffhandItemAttack(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(ClientMessageOffhandItemAttack packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.targetId);
    }

    public static ClientMessageOffhandItemAttack decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandItemAttack(buffer.readVarInt());
    }

    public static void handle(ClientMessageOffhandItemAttack packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandItemCombat.attack(player, packet.targetId);
            }
        });
        context.setPacketHandled(true);
    }
}
