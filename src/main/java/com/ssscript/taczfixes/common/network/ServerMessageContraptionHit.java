package com.ssscript.taczfixes.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

public class ServerMessageContraptionHit {
    private final double hitX;
    private final double hitY;
    private final double hitZ;
    private final Direction direction;
    private final int stateId;
    private final String ammoId;
    private final String gunId;
    private final String gunDisplayId;
    private final boolean incendiary;

    public ServerMessageContraptionHit(Vec3 hit, Direction direction, BlockState state,
                                       String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        this.hitX = hit.x;
        this.hitY = hit.y;
        this.hitZ = hit.z;
        this.direction = direction;
        this.stateId = Block.getId(state);
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.incendiary = incendiary;
    }

    private ServerMessageContraptionHit(double hitX, double hitY, double hitZ, Direction direction, int stateId,
                                        String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.direction = direction;
        this.stateId = stateId;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.incendiary = incendiary;
    }

    public static void encode(ServerMessageContraptionHit msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.hitX);
        buf.writeDouble(msg.hitY);
        buf.writeDouble(msg.hitZ);
        buf.writeEnum(msg.direction);
        buf.writeVarInt(msg.stateId);
        buf.writeUtf(msg.ammoId);
        buf.writeUtf(msg.gunId);
        buf.writeUtf(msg.gunDisplayId);
        buf.writeBoolean(msg.incendiary);
    }

    public static ServerMessageContraptionHit decode(FriendlyByteBuf buf) {
        return new ServerMessageContraptionHit(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readEnum(Direction.class), buf.readVarInt(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(ServerMessageContraptionHit msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.compat.create.ContraptionHitEffects.spawn(
                        new Vec3(msg.hitX, msg.hitY, msg.hitZ), msg.direction,
                        Block.stateById(msg.stateId), msg.ammoId, msg.gunId, msg.gunDisplayId, msg.incendiary)));
        context.setPacketHandled(true);
    }
}
