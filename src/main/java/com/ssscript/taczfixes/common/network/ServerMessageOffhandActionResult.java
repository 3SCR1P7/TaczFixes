package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.render.ClientOffhandNetworkHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/network/packet/ServerMessageOffhandActionResult.class */
public final class ServerMessageOffhandActionResult {
    public static final int ACTION_SHOOT = 0;
    public static final int ACTION_RELOAD = 1;
    public static final int ACTION_BOLT = 2;
    public static final int ACTION_FIRE_SELECT = 3;
    public static final int ACTION_CANCEL_RELOAD = 4;
    private final int actionType;
    private final UUID requestedStackId;
    private final int requestId;
    private final long shootTimestamp;
    private final boolean accepted;
    private final boolean stackMatched;
    private final int authoritativeFireMode;
    private final long authoritativeShootCoolDown;

    public ServerMessageOffhandActionResult(int actionType, UUID requestedStackId, int requestId, long shootTimestamp, boolean accepted, boolean stackMatched, int authoritativeFireMode, long authoritativeShootCoolDown) {
        this.actionType = actionType;
        this.requestedStackId = requestedStackId;
        this.requestId = requestId;
        this.shootTimestamp = shootTimestamp;
        this.accepted = accepted;
        this.stackMatched = stackMatched;
        this.authoritativeFireMode = authoritativeFireMode;
        this.authoritativeShootCoolDown = authoritativeShootCoolDown;
    }

    public static void encode(ServerMessageOffhandActionResult packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.actionType);
        buffer.writeUUID(packet.requestedStackId);
        buffer.writeVarInt(packet.requestId);
        buffer.writeLong(packet.shootTimestamp);
        buffer.writeBoolean(packet.accepted);
        buffer.writeBoolean(packet.stackMatched);
        buffer.writeInt(packet.authoritativeFireMode);
        buffer.writeLong(packet.authoritativeShootCoolDown);
    }

    public static ServerMessageOffhandActionResult decode(FriendlyByteBuf buffer) {
        return new ServerMessageOffhandActionResult(buffer.readUnsignedByte(), buffer.readUUID(), buffer.readVarInt(), buffer.readLong(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readLong());
    }

    public static void handle(ServerMessageOffhandActionResult packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> {
                return () -> {
                    ClientOffhandNetworkHandler.handleActionResult(packet.actionType, packet.requestedStackId, packet.requestId, packet.shootTimestamp, packet.accepted, packet.stackMatched, packet.authoritativeFireMode, packet.authoritativeShootCoolDown);
                };
            });
        });
        context.setPacketHandled(true);
    }
}
