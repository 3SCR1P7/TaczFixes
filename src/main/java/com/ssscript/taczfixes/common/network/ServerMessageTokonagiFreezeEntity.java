package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端→客户端(追踪该实体的玩家): 同步某实体被常凪冻结的剩余 tick(0 = 解除)。 */
public class ServerMessageTokonagiFreezeEntity {
    private final int entityId;
    private final int ticks;

    public ServerMessageTokonagiFreezeEntity(int entityId, int ticks) {
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public static void encode(ServerMessageTokonagiFreezeEntity message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeVarInt(message.ticks);
    }

    public static ServerMessageTokonagiFreezeEntity decode(FriendlyByteBuf buf) {
        return new ServerMessageTokonagiFreezeEntity(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ServerMessageTokonagiFreezeEntity message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FreezeClientState.setEntity(message.entityId, message.ticks)));
        context.setPacketHandled(true);
    }
}
