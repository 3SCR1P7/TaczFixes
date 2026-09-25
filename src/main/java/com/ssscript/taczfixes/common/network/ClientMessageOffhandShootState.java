package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.compat.ArcanaSkillBridge;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 副手开火键按住状态 (用于 Arcana 技能桥接的 ON_CLICK / ON_AUTO_SHOOT)。 */
public final class ClientMessageOffhandShootState {

    private final boolean down;

    public ClientMessageOffhandShootState(boolean down) {
        this.down = down;
    }

    public static void encode(ClientMessageOffhandShootState packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.down);
    }

    public static ClientMessageOffhandShootState decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandShootState(buffer.readBoolean());
    }

    public static void handle(ClientMessageOffhandShootState packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ArcanaSkillBridge.onOffhandShootState(player, packet.down);
            }
        });
        context.setPacketHandled(true);
    }
}
