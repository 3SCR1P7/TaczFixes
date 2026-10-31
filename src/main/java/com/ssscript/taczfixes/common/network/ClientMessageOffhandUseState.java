package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandUseHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class ClientMessageOffhandUseState {
    private final boolean using;

    public ClientMessageOffhandUseState(boolean using) {
        this.using = using;
    }

    public static void encode(ClientMessageOffhandUseState packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.using);
    }

    public static ClientMessageOffhandUseState decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandUseState(buffer.readBoolean());
    }

    public static void handle(ClientMessageOffhandUseState packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandUseHandler.setUsing(player, packet.using);
            }
        });
        context.setPacketHandled(true);
    }
}
