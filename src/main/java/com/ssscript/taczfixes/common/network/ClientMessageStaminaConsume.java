package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.StaminaHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端→服务端: ParCool 消耗了耐力, 按倍率消耗本模组耐力。 */
public class ClientMessageStaminaConsume {

    private final float parcoolAmount;

    public ClientMessageStaminaConsume(float parcoolAmount) {
        this.parcoolAmount = parcoolAmount;
    }

    public static void encode(ClientMessageStaminaConsume message, FriendlyByteBuf buf) {
        buf.writeFloat(message.parcoolAmount);
    }

    public static ClientMessageStaminaConsume decode(FriendlyByteBuf buf) {
        return new ClientMessageStaminaConsume(buf.readFloat());
    }

    public static void handle(ClientMessageStaminaConsume message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!Config.STAMINA_ENABLED.get()) return;
            float amount = Math.max(0f, message.parcoolAmount)
                    * Config.STAMINA_PARCOOL_CONSUMPTION_MULTIPLIER.get().floatValue()
                    * (1f + StaminaHelper.gunWeight(player) * Config.STAMINA_WEIGHT_CONSUMPTION_PER_KG.get().floatValue())
                    * StaminaHelper.consumptionMultiplier(player);
            StaminaHelper.consume(player, amount);
        });
        context.setPacketHandled(true);
    }
}
