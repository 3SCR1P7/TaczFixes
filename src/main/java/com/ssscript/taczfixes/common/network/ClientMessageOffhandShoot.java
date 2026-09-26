package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.OffhandShooterManager;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class ClientMessageOffhandShoot {
    private final UUID stackId;
    private final long timestamp;
    private final float chargeProgress;

    public ClientMessageOffhandShoot(UUID stackId, long timestamp, float chargeProgress) {
        this.stackId = stackId;
        this.timestamp = timestamp;
        this.chargeProgress = chargeProgress;
    }

    public static void encode(ClientMessageOffhandShoot packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
        buffer.writeLong(packet.timestamp);
        buffer.writeFloat(packet.chargeProgress);
    }

    public static ClientMessageOffhandShoot decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandShoot(buffer.readUUID(), buffer.readLong(), buffer.readFloat());
    }

    public static void handle(ClientMessageOffhandShoot packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                OffhandShooterManager.shoot(player, packet.stackId, packet.timestamp, packet.chargeProgress);
            }
        });
        context.setPacketHandled(true);
    }
}
