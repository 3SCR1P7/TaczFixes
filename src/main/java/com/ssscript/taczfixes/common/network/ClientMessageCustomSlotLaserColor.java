package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageCustomSlotLaserColor {
    private final String slotId;
    private final int color;

    public ClientMessageCustomSlotLaserColor(String slotId, int color) {
        this.slotId = slotId;
        this.color = color;
    }

    public static void encode(ClientMessageCustomSlotLaserColor message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
        buf.writeInt(message.color);
    }

    public static ClientMessageCustomSlotLaserColor decode(FriendlyByteBuf buf) {
        return new ClientMessageCustomSlotLaserColor(buf.readUtf(), buf.readInt());
    }

    public static void handle(ClientMessageCustomSlotLaserColor message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageCustomSlotLaserColor message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, message.slotId);
        if (def == null) return;

        ItemStack item = CustomSlotStorage.get(gunStack, message.slotId);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        attachment.setLaserColor(item, message.color);
        CompoundTag tag = gunStack.getOrCreateTag();
        CompoundTag slots = tag.contains(CustomSlotStorage.TAG_KEY, 10)
                ? tag.getCompound(CustomSlotStorage.TAG_KEY) : new CompoundTag();
        slots.put(message.slotId, item.save(new CompoundTag()));
        tag.put(CustomSlotStorage.TAG_KEY, slots);
        player.inventoryMenu.broadcastChanges();
    }
}