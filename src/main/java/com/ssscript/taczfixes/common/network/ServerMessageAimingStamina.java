package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端→客户端: 同步上肢耐力(当前值/上限)。 */
public class ServerMessageAimingStamina {

    private final float stamina;
    private final float max;

    public ServerMessageAimingStamina(float stamina, float max) {
        this.stamina = stamina;
        this.max = max;
    }

    public static void encode(ServerMessageAimingStamina message, FriendlyByteBuf buf) {
        buf.writeFloat(message.stamina);
        buf.writeFloat(message.max);
    }

    public static ServerMessageAimingStamina decode(FriendlyByteBuf buf) {
        return new ServerMessageAimingStamina(buf.readFloat(), buf.readFloat());
    }

    public static void handle(ServerMessageAimingStamina message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AimingStaminaClientState.set(message.stamina, message.max)));
        context.setPacketHandled(true);
    }
}
