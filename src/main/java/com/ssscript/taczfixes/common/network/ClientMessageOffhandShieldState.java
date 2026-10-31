package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShieldHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class ClientMessageOffhandShieldState {
    private final boolean raised;

    public ClientMessageOffhandShieldState(boolean raised) {
        this.raised = raised;
    }

    public static void encode(ClientMessageOffhandShieldState packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.raised);
    }

    public static ClientMessageOffhandShieldState decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandShieldState(buffer.readBoolean());
    }

    public static void handle(ClientMessageOffhandShieldState packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShieldHandler.setRaised(player, packet.raised);
            }
        });
        context.setPacketHandled(true);
    }
}
