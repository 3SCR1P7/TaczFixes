package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.StaminaHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端→服务端: 直接消耗指定数值的耐力(如 ParCool 滑铲)。 */
public class ClientMessageStaminaConsumeRaw {

    private final float amount;

    public ClientMessageStaminaConsumeRaw(float amount) {
        this.amount = amount;
    }

    public static void encode(ClientMessageStaminaConsumeRaw message, FriendlyByteBuf buf) {
        buf.writeFloat(message.amount);
    }

    public static ClientMessageStaminaConsumeRaw decode(FriendlyByteBuf buf) {
        return new ClientMessageStaminaConsumeRaw(buf.readFloat());
    }

    public static void handle(ClientMessageStaminaConsumeRaw message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!Config.STAMINA_ENABLED.get()) return;
            StaminaHelper.consumeWithGunMultiplier(player, Math.max(0f, message.amount));
        });
        context.setPacketHandled(true);
    }
}
