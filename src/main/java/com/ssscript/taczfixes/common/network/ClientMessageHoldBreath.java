package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.AimingStaminaState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端→服务端: 屏息按键状态。 */
public class ClientMessageHoldBreath {

    private final boolean holding;

    public ClientMessageHoldBreath(boolean holding) {
        this.holding = holding;
    }

    public static void encode(ClientMessageHoldBreath message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.holding);
    }

    public static ClientMessageHoldBreath decode(FriendlyByteBuf buf) {
        return new ClientMessageHoldBreath(buf.readBoolean());
    }

    public static void handle(ClientMessageHoldBreath message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            AimingStaminaState.setHoldingBreath(player.getUUID(), message.holding);
        });
        context.setPacketHandled(true);
    }
}
