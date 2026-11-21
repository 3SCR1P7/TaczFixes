package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

/** 服务端→客户端: 同步常凪冻结的剩余 tick(0 = 解除)。 */
public class ServerMessageTokonagiFreeze {
    private final int ticks;

    public ServerMessageTokonagiFreeze(int ticks) {
        this.ticks = ticks;
    }

    public static void encode(ServerMessageTokonagiFreeze message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.ticks);
    }

    public static ServerMessageTokonagiFreeze decode(FriendlyByteBuf buf) {
        return new ServerMessageTokonagiFreeze(buf.readVarInt());
    }

    public static void handle(ServerMessageTokonagiFreeze message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> FreezeClientState.set(message.ticks)));
        context.setPacketHandled(true);
    }
}
