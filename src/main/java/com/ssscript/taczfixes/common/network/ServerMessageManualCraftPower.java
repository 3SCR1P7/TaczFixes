package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

public class ServerMessageManualCraftPower {
    private final int containerId;

    public ServerMessageManualCraftPower(int containerId) {
        this.containerId = containerId;
    }

    public static void encode(ServerMessageManualCraftPower message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.containerId);
    }

    public static ServerMessageManualCraftPower decode(FriendlyByteBuf buf) {
        return new ServerMessageManualCraftPower(buf.readVarInt());
    }

    public static void handle(ServerMessageManualCraftPower message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.util.GunSmithTableAutoCraftClientState
                        .handleManualPowerError(message.containerId)));
        context.setPacketHandled(true);
    }
}
