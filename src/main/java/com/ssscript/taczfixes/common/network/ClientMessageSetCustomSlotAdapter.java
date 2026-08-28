package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageSetCustomSlotAdapter {
    private final String slotId;
    private final ResourceLocation adapterId;

    public ClientMessageSetCustomSlotAdapter(String slotId, ResourceLocation adapterId) {
        this.slotId = slotId;
        this.adapterId = adapterId;
    }

    public static void encode(ClientMessageSetCustomSlotAdapter message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
        if (message.adapterId == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeResourceLocation(message.adapterId);
        }
    }

    public static ClientMessageSetCustomSlotAdapter decode(FriendlyByteBuf buf) {
        String slotId = buf.readUtf();
        ResourceLocation adapterId = null;
        if (buf.readBoolean()) {
            adapterId = buf.readResourceLocation();
        }
        return new ClientMessageSetCustomSlotAdapter(slotId, adapterId);
    }

    public static void handle(ClientMessageSetCustomSlotAdapter message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack gunStack = player.getMainHandItem();
            if (IGun.getIGunOrNull(gunStack) == null) return;
            CustomSlotStorage.setAdapter(gunStack, message.slotId, message.adapterId);
            player.inventoryMenu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
