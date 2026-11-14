package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.compat.ArcanaSkillBridge;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 副手技能动作键 (Arcana 的 ACTION_1..4 按键在副手补发一次)。 */
public final class ClientMessageOffhandAction {

    private final int action;

    public ClientMessageOffhandAction(int action) {
        this.action = action;
    }

    public static void encode(ClientMessageOffhandAction packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action);
    }

    public static ClientMessageOffhandAction decode(FriendlyByteBuf buffer) {
        return new ClientMessageOffhandAction(buffer.readVarInt());
    }

    public static void handle(ClientMessageOffhandAction packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 101/102: 主手为物品时切换副手枪械的 Arcana 激光/手电状态。
            if (packet.action == 101 || packet.action == 102) {
                net.minecraft.world.item.ItemStack offhand = player.getOffhandItem();
                if (com.tacz.guns.api.item.IGun.getIGunOrNull(offhand) == null) {
                    return;
                }
                if (packet.action == 101) {
                    com.ssscript.taczfixes.common.compat.ArcanaToggleBridge.toggleLaser(offhand);
                } else {
                    com.ssscript.taczfixes.common.compat.ArcanaToggleBridge.toggleFlashlight(offhand);
                }
                return;
            }
            ArcanaSkillBridge.triggerOffhandAction(player, packet.action);
        });
        context.setPacketHandled(true);
    }
}
