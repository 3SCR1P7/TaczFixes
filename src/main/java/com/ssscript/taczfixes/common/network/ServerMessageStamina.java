package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.client.util.StaminaClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

/** 鏈嶅姟绔啋瀹㈡埛绔? 鍚屾鑰愬姏(褰撳墠鍊?涓婇檺/鏄惁鍔涚)銆?*/
public class ServerMessageStamina {

    private final float stamina;
    private final float max;
    private final boolean exhausted;

    public ServerMessageStamina(float stamina, float max, boolean exhausted) {
        this.stamina = stamina;
        this.max = max;
        this.exhausted = exhausted;
    }

    public static void encode(ServerMessageStamina message, FriendlyByteBuf buf) {
        buf.writeFloat(message.stamina);
        buf.writeFloat(message.max);
        buf.writeBoolean(message.exhausted);
    }

    public static ServerMessageStamina decode(FriendlyByteBuf buf) {
        return new ServerMessageStamina(buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(ServerMessageStamina message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> StaminaClientState.set(message.stamina, message.max, message.exhausted)));
        context.setPacketHandled(true);
    }
}
