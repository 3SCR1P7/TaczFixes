package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageInstallCustomSlot {
    private final int slotIndex;
    private final String slotId;

    public ClientMessageInstallCustomSlot(int slotIndex, String slotId) {
        this.slotIndex = slotIndex;
        this.slotId = slotId;
    }

    public static void encode(ClientMessageInstallCustomSlot message, FriendlyByteBuf buf) {
        buf.writeInt(message.slotIndex);
        buf.writeUtf(message.slotId);
    }

    public static ClientMessageInstallCustomSlot decode(FriendlyByteBuf buf) {
        return new ClientMessageInstallCustomSlot(buf.readInt(), buf.readUtf());
    }

    public static void handle(ClientMessageInstallCustomSlot message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageInstallCustomSlot message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, message.slotId);
        if (def == null) {
            return;
        }

        boolean liberated = LiberateCompat.isLiberated(player);
        net.minecraft.world.entity.player.Inventory inventory = LiberateCompat.getVirtualInventory(player.getInventory());
        ItemStack item = inventory.getItem(message.slotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        if (!gun.allowAttachment(gunStack, item)) {
            return;
        }
        boolean match = CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(item), attachment.getType(item));
        if (!liberated && !match) {
            return;
        }
        if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) {
            return;
        }

        java.util.Map<String, CustomSlotDefinition> allSlots = CustomSlotManager.getSlots(gunId);
        java.util.Set<String> toUnload = new java.util.LinkedHashSet<>(def.getConflict().keySet());
        for (java.util.Map.Entry<String, CustomSlotDefinition> entry : allSlots.entrySet()) {
            if (entry.getKey().equals(message.slotId)) continue;
            if (entry.getValue().getConflict().containsKey(message.slotId)) {
                toUnload.add(entry.getKey());
            }
        }
        for (String conflictId : toUnload) {
            ItemStack removed = CustomSlotManager.getSlot(gunId, conflictId) != null
                    ? CustomSlotStorage.unload(gunStack, conflictId)
                    : unloadStandard(gunStack, gun, conflictId);
            if (!removed.isEmpty() && !liberated) {
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
            }
        }

        ItemStack old = CustomSlotStorage.get(gunStack, message.slotId);
        if (!old.isEmpty() && !liberated) {
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }

        CustomSlotStorage.install(gunStack, message.slotId, item.copy());
        if (inventory == player.getInventory()) {
            player.getInventory().setItem(message.slotIndex, ItemStack.EMPTY);
        }
        CustomSlotManager.cascadeUnloadDependents(player, gunStack);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }

    private static ItemStack unloadStandard(ItemStack gunStack, IGun gun, String refId) {
        try {
            com.tacz.guns.api.item.attachment.AttachmentType type =
                    com.tacz.guns.api.item.attachment.AttachmentType.valueOf(refId.toUpperCase());
            ItemStack removed = gun.getAttachment(gunStack, type);
            if (removed.isEmpty()) return ItemStack.EMPTY;
            gun.unloadAttachment(gunStack, type);
            return removed;
        } catch (IllegalArgumentException ex) {
            return ItemStack.EMPTY;
        }
    }
}
