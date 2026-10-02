package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.google.gson.JsonElement;
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
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunStack, message.slotId);
        if (def == null) {
            return;
        }

        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        ItemStack item = inventory.getItem(message.slotIndex);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return;
        if (!isAllowAttachmentForSlot(gunStack, message.slotId, item)) {
            return;
        }
            boolean match = CustomSlotManager.matchesSlot(def, gunId, attachment.getAttachmentId(item), attachment.getType(item));
            if (CustomSlotManager.matchesBlacklist(def, attachment.getAttachmentId(item))) {
                return;
            }
            if (!match) {
            return;
        }
        if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) {
            return;
        }
        // 该配件属于本槽原厂候选: 免费装上, 不消耗背包物品
        boolean builtin = CustomSlotManager.isBuiltinCandidate(def, attachment.getAttachmentId(item));
        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);

            java.util.Map<String, CustomSlotDefinition> allSlots = CustomSlotManager.getSlots(gunStack);
        java.util.Set<String> toUnload = new java.util.LinkedHashSet<>();
        for (java.util.Map.Entry<String, JsonElement> conflictEntry : def.getConflict().entrySet()) {
            if (CustomSlotManager.satisfies(gunId, gunStack, conflictEntry.getKey(), conflictEntry.getValue())) {
                toUnload.add(conflictEntry.getKey());
            }
        }
        for (java.util.Map.Entry<String, CustomSlotDefinition> entry : allSlots.entrySet()) {
            if (entry.getKey().equals(message.slotId)) continue;
            JsonElement cond = entry.getValue().getConflict().get(message.slotId);
            if (cond != null && CustomSlotManager.satisfies(gunId, gunStack, entry.getKey(), cond)) {
                toUnload.add(entry.getKey());
            }
        }
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total != null) {
            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
            int delta = -AttachmentTaczFixesManager.getRefitPointConsume(
                    CustomSlotStorage.get(gunStack, message.slotId));
            for (String conflictId : toUnload) {
                delta -= refitPointInSlot(gunStack, gun, conflictId);
            }
            delta += AttachmentTaczFixesManager.getRefitPointConsume(item);
            if (used + delta > total) {
                return;
            }
        }
        for (String conflictId : toUnload) {
                ItemStack removed = CustomSlotManager.getSlot(gunStack, conflictId) != null
                    ? CustomSlotStorage.unload(gunStack, conflictId)
                    : unloadStandard(gunStack, gun, conflictId);
            if (!removed.isEmpty() && !virtual
                    && !com.tacz.guns.util.VirtualOemAttachment.isMarked(removed)) {
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
            }
        }

        ItemStack old = CustomSlotStorage.getPhysical(gunStack, message.slotId);
        if (!old.isEmpty() && !virtual
                && !com.tacz.guns.util.VirtualOemAttachment.isMarked(old)) {
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }

        if (builtin) {
            CustomSlotStorage.installBuiltin(gunStack, message.slotId, attachment.getAttachmentId(item));
        } else {
            ItemStack toInstall = item.copy();
            if (virtual) {
                com.tacz.guns.util.VirtualOemAttachment.mark(toInstall);
            }
            CustomSlotStorage.install(gunStack, message.slotId, toInstall);
            if (!virtual) {
                player.getInventory().setItem(message.slotIndex, ItemStack.EMPTY);
            }
        }
        CustomSlotManager.cascadeUnloadDependents(player, gunStack);
        CustomSlotManager.cascadeUnloadConflicts(player, gunStack);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }

    /**
     * 按槽位适配器校验(与客户端 MixinGunRefitScreenAttachmentList 过滤一致):
     * 无适配器 → 仅直连允许; 有适配器 → 适配器允许该配件。
     * 原版 gun.allowAttachment 读共享 SlotAdapters NBT, 自定义槽位需按自身适配器判定。
     */
    private static boolean isAllowAttachmentForSlot(ItemStack gun, String slotId, ItemStack item) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(item);
        if (attachment == null) return false;
        ResourceLocation attachmentId = attachment.getAttachmentId(item);
        if (attachmentId == null) return false;
        ResourceLocation adapterId = CustomSlotStorage.getAdapter(gun, slotId);
        if (adapterId == null) {
            return com.tacz.guns.util.SlotAdapterHelper.allowsDirectAttachment(gun, attachmentId);
        }
        return com.tacz.guns.api.TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .map(index -> index.allowsAttachment(attachmentId))
                .orElse(false);
    }

    private static int refitPointInSlot(ItemStack gunStack, IGun gun, String refId) {
        ItemStack stack = CustomSlotStorage.get(gunStack, refId);
        if (stack.isEmpty()) {
            try {
                com.tacz.guns.api.item.attachment.AttachmentType type =
                        com.tacz.guns.api.item.attachment.AttachmentType.valueOf(refId.toUpperCase());
                stack = gun.getAttachment(gunStack, type);
            } catch (IllegalArgumentException ex) {
                return 0;
            }
        }
        return AttachmentTaczFixesManager.getRefitPointConsume(stack);
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
