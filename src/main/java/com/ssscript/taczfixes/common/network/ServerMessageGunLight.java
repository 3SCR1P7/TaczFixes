package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

/** 鏈嶅姟绔?-> 瀹㈡埛绔? 鐢卞瓙寮瑰懡涓?閿€姣佽Е鍙戠殑鏋鍔ㄦ€佸厜鐓?鐖嗙偢鍏夋垨鍥炵▼ bullet 绾挎鍏?, 鍙傛暟鐢辨湇鍔＄鐩存帴涓嬪彂銆?*/
public class ServerMessageGunLight {

    private final int color;
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

    public ServerMessageGunLight(boolean explosion, int time, int levelMax, int levelMin, int color,
                                 double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ) {
        this.color = color;
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
        buf.writeInt(msg.color);
        buf.writeDouble(msg.fromX);
        buf.writeDouble(msg.fromY);
        buf.writeDouble(msg.fromZ);
        buf.writeDouble(msg.toX);
        buf.writeDouble(msg.toY);
        buf.writeDouble(msg.toZ);
    }

    public static ServerMessageGunLight decode(FriendlyByteBuf buf) {
        return new ServerMessageGunLight(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ServerMessageGunLight msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.render.ClientGunLightManager.onLightPacket(
                        msg.explosion, msg.time, msg.levelMax, msg.levelMin, msg.color,
                        new net.minecraft.world.phys.Vec3(msg.fromX, msg.fromY, msg.fromZ),
                        new net.minecraft.world.phys.Vec3(msg.toX, msg.toY, msg.toZ))));
        context.setPacketHandled(true);
    }
}
