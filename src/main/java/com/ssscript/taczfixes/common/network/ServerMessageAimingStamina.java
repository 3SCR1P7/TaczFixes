package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

/** 鏈嶅姟绔啋瀹㈡埛绔? 鍚屾涓婅偄鑰愬姏(褰撳墠鍊?涓婇檺)銆?*/
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
