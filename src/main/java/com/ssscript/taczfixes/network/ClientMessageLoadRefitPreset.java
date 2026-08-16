package com.ssscript.taczfixes.network;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.LiberateCompat;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class ClientMessageLoadRefitPreset {
    private final Map<String, ResourceLocation> preset;

    public ClientMessageLoadRefitPreset(Map<String, ResourceLocation> preset) {
        this.preset = preset;
    }

    public static void encode(ClientMessageLoadRefitPreset message, FriendlyByteBuf buf) {
        buf.writeInt(message.preset.size());
        for (Map.Entry<String, ResourceLocation> entry : message.preset.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue().toString());
        }
    }

    public static ClientMessageLoadRefitPreset decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, ResourceLocation> preset = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            ResourceLocation attachmentId = ResourceLocation.tryParse(buf.readUtf());
            if (attachmentId != null) {
                preset.put(key, attachmentId);
            }
        }
        return new ClientMessageLoadRefitPreset(preset);
    }

    public static void handle(ClientMessageLoadRefitPreset message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageLoadRefitPreset message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        boolean liberated = LiberateCompat.isLiberated(player);
        Inventory inventory = player.getInventory();

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack removed = gun.getAttachment(gunStack, type);
            if (removed.isEmpty()) continue;
            gun.unloadAttachment(gunStack, type);
            if (type == AttachmentType.EXTENDED_MAG) {
                gun.dropAllAmmo(player, gunStack);
            }
            if (!liberated && !inventory.add(removed)) {
                player.drop(removed, false);
            }
        }
        for (Map.Entry<String, CustomSlotDefinition> entry : CustomSlotManager.getSlots(gunId).entrySet()) {
            ItemStack removed = CustomSlotStorage.unload(gunStack, entry.getKey());
            if (removed.isEmpty()) continue;
            if (!liberated && !inventory.add(removed)) {
                player.drop(removed, false);
            }
        }

        for (Map.Entry<String, ResourceLocation> entry : message.preset.entrySet()) {
            String slotKey = entry.getKey();
            ResourceLocation attachmentId = entry.getValue();
            CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, slotKey);
            if (def != null) {
                installCustom(player, gun, gunStack, gunId, slotKey, def, attachmentId, liberated);
                continue;
            }
            AttachmentType type;
            try {
                type = AttachmentType.valueOf(slotKey.toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (type == AttachmentType.NONE) continue;
            installStandard(player, gun, gunStack, type, attachmentId, liberated);
        }

        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }

    private static void installStandard(ServerPlayer player, IGun gun, ItemStack gunStack,
                                        AttachmentType type, ResourceLocation attachmentId, boolean liberated) {
        Inventory source = liberated ? LiberateCompat.getVirtualInventory(player.getInventory()) : player.getInventory();
        ItemStack item = findAndTake(player, source, attachmentId, gun, gunStack, null, liberated);
        if (item.isEmpty()) return;
        gun.installAttachment(gunStack, item);
    }

    private static void installCustom(ServerPlayer player, IGun gun, ItemStack gunStack, ResourceLocation gunId,
                                      String slotKey, CustomSlotDefinition def, ResourceLocation attachmentId,
                                      boolean liberated) {
        if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) return;
        Inventory source = liberated ? LiberateCompat.getVirtualInventory(player.getInventory()) : player.getInventory();
        ItemStack item = findAndTake(player, source, attachmentId, gun, gunStack, def, liberated);
        if (item.isEmpty()) return;
        CustomSlotStorage.install(gunStack, slotKey, item.copy());
    }

    private static ItemStack findAndTake(ServerPlayer player, Inventory source, ResourceLocation attachmentId,
                                         IGun gun, ItemStack gunStack, CustomSlotDefinition def, boolean liberated) {
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            if (!attachmentId.equals(attachment.getAttachmentId(stack))) continue;
            if (!gun.allowAttachment(gunStack, stack)) continue;
            if (def != null) {
                boolean match = CustomSlotManager.matchesSlot(def, gun.getGunId(gunStack),
                        attachmentId, attachment.getType(stack));
                if (!liberated && !match) continue;
            }
            if (source == player.getInventory()) {
                source.setItem(i, ItemStack.EMPTY);
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }
}