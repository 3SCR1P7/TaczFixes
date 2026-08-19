package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageCustomSlotZoom {
    private final String slotId;

    public ClientMessageCustomSlotZoom(String slotId) {
        this.slotId = slotId;
    }

    public static void encode(ClientMessageCustomSlotZoom message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
    }

    public static ClientMessageCustomSlotZoom decode(FriendlyByteBuf buf) {
        return new ClientMessageCustomSlotZoom(buf.readUtf());
    }

    public static void handle(ClientMessageCustomSlotZoom message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageCustomSlotZoom message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;

        ItemStack scope = CustomSlotStorage.get(gunStack, message.slotId);
        if (scope.isEmpty()) return;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        if (attachment == null) return;
        ResourceLocation id = attachment.getAttachmentId(scope);
        if (id == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id)) return;

        CompoundTag tag = scope.getOrCreateTag();
        int number = AttachmentItemDataAccessor.getZoomNumberFromTag(tag);
        AttachmentItemDataAccessor.setZoomNumberToTag(tag, (number + 1) % 2147483646);

        CompoundTag gunTag = gunStack.getOrCreateTag();
        CompoundTag slots = gunTag.getCompound(CustomSlotStorage.TAG_KEY);
        slots.put(message.slotId, scope.save(new CompoundTag()));
        gunTag.put(CustomSlotStorage.TAG_KEY, slots);
        player.inventoryMenu.broadcastChanges();
    }
}