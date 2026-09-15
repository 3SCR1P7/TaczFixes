package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.render.ClientOffhandNetworkHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/network/packet/ServerMessageOffhandState.class */
public final class ServerMessageOffhandState {
    private final UUID stackId;
    private final int reloadRequestId;
    private final int reloadStateType;
    private final long reloadElapsedMillis;
    private final long reloadCountDownMillis;
    private final boolean bolting;
    private final int boltRequestId;
    private final boolean boltRequestAcknowledged;
    private final boolean manualActionEpisodeActive;
    private final boolean manualActionBoltReady;
    private final boolean shootRequestAcknowledged;
    private final long acknowledgedShootTimestamp;

    public ServerMessageOffhandState(UUID stackId, int reloadRequestId, int reloadStateType, long reloadElapsedMillis, long reloadCountDownMillis, boolean bolting, int boltRequestId, boolean boltRequestAcknowledged, boolean manualActionEpisodeActive, boolean manualActionBoltReady, boolean shootRequestAcknowledged, long acknowledgedShootTimestamp) {
        this.stackId = stackId;
        this.reloadRequestId = reloadRequestId;
        this.reloadStateType = reloadStateType;
        this.reloadElapsedMillis = reloadElapsedMillis;
        this.reloadCountDownMillis = reloadCountDownMillis;
        this.bolting = bolting;
        this.boltRequestId = boltRequestId;
        this.boltRequestAcknowledged = boltRequestAcknowledged;
        this.manualActionEpisodeActive = manualActionEpisodeActive;
        this.manualActionBoltReady = manualActionBoltReady;
        this.shootRequestAcknowledged = shootRequestAcknowledged;
        this.acknowledgedShootTimestamp = acknowledgedShootTimestamp;
    }

    public static void encode(ServerMessageOffhandState packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.stackId);
        buffer.writeVarInt(packet.reloadRequestId);
        buffer.writeVarInt(packet.reloadStateType);
        buffer.writeVarLong(packet.reloadElapsedMillis);
        buffer.writeVarLong(packet.reloadCountDownMillis);
        buffer.writeBoolean(packet.bolting);
        buffer.writeVarInt(packet.boltRequestId);
        buffer.writeBoolean(packet.boltRequestAcknowledged);
        buffer.writeBoolean(packet.manualActionEpisodeActive);
        buffer.writeBoolean(packet.manualActionBoltReady);
        buffer.writeBoolean(packet.shootRequestAcknowledged);
        buffer.writeLong(packet.acknowledgedShootTimestamp);
    }

    public static ServerMessageOffhandState decode(FriendlyByteBuf buffer) {
        return new ServerMessageOffhandState(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readLong());
    }

    public static void handle(ServerMessageOffhandState packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> {
                return () -> {
                    ClientOffhandNetworkHandler.handleState(packet.stackId, packet.reloadRequestId, packet.reloadStateType, packet.reloadElapsedMillis, packet.reloadCountDownMillis, packet.bolting, packet.boltRequestId, packet.boltRequestAcknowledged, packet.manualActionEpisodeActive, packet.manualActionBoltReady, packet.shootRequestAcknowledged, packet.acknowledgedShootTimestamp);
                };
            });
        });
        context.setPacketHandled(true);
    }
}
