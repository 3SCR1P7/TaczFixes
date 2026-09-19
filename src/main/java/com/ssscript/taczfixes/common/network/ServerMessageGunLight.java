package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 -> 客户端: 由子弹命中/销毁触发的枪械动态光照(爆炸光或回程 bullet 线段光), 参数由服务端直接下发。 */
public class ServerMessageGunLight {

    private final boolean explosion;
    private final int time;
    private final int levelMax;
    private final int levelMin;
    private final double fromX;
    private final double fromY;
    private final double fromZ;
    private final double toX;
    private final double toY;
    private final double toZ;

    public ServerMessageGunLight(boolean explosion, int time, int levelMax, int levelMin,
                                 double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ) {
        this.explosion = explosion;
        this.time = time;
        this.levelMax = levelMax;
        this.levelMin = levelMin;
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
    }

    public static void encode(ServerMessageGunLight msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.explosion);
        buf.writeVarInt(msg.time);
        buf.writeVarInt(msg.levelMax);
        buf.writeVarInt(msg.levelMin);
        buf.writeDouble(msg.fromX);
        buf.writeDouble(msg.fromY);
        buf.writeDouble(msg.fromZ);
        buf.writeDouble(msg.toX);
        buf.writeDouble(msg.toY);
        buf.writeDouble(msg.toZ);
    }

    public static ServerMessageGunLight decode(FriendlyByteBuf buf) {
        return new ServerMessageGunLight(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ServerMessageGunLight msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.render.ClientGunLightManager.onLightPacket(
                        msg.explosion, msg.time, msg.levelMax, msg.levelMin,
                        new net.minecraft.world.phys.Vec3(msg.fromX, msg.fromY, msg.fromZ),
                        new net.minecraft.world.phys.Vec3(msg.toX, msg.toY, msg.toZ))));
        context.setPacketHandled(true);
    }
}
