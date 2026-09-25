package com.ssscript.taczfixes.common.network;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.LiberateCompat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/** 客户端在改装界面点击自定义槽位的原厂候选件: 不消耗背包, 直接装上虚拟原厂件。 */
public class ClientMessageInstallCustomSlotBuiltin {
    private final String slotId;
    private final ResourceLocation attachmentId;

    public ClientMessageInstallCustomSlotBuiltin(String slotId, ResourceLocation attachmentId) {
        this.slotId = slotId;
        this.attachmentId = attachmentId;
    }

    public static void encode(ClientMessageInstallCustomSlotBuiltin message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
        buf.writeResourceLocation(message.attachmentId);
    }

    public static ClientMessageInstallCustomSlotBuiltin decode(FriendlyByteBuf buf) {
        return new ClientMessageInstallCustomSlotBuiltin(buf.readUtf(), buf.readResourceLocation());
    }

    public static void handle(ClientMessageInstallCustomSlotBuiltin message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageInstallCustomSlotBuiltin message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, message.slotId);
        if (def == null) return;
        if (!CustomSlotManager.isBuiltinCandidate(def, message.attachmentId)) return;
        if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) return;

        boolean liberated = LiberateCompat.isLiberated(player);
        java.util.Map<String, CustomSlotDefinition> allSlots = CustomSlotManager.getSlots(gunId);
        Set<String> toUnload = new LinkedHashSet<>();
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

        boolean virtual = com.ssscript.taczfixes.common.util.VirtualAttachments.isActive(player);
        Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
        if (total != null) {
            int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
            int delta = -AttachmentTaczFixesManager.getRefitPointConsume(
                    CustomSlotStorage.getPhysical(gunStack, message.slotId));
            for (String conflictId : toUnload) {
                delta -= refitPointInSlot(gunStack, gun, conflictId);
            }
            if (used + delta > total) {
                return;
            }
        }

        for (String conflictId : toUnload) {
            ItemStack removed = CustomSlotManager.getSlot(gunId, conflictId) != null
                    ? CustomSlotStorage.unload(gunStack, conflictId)
                    : unloadStandard(gunStack, gun, conflictId);
            if (!removed.isEmpty() && !liberated && !virtual
                    && !com.tacz.guns.util.VirtualOemAttachment.isMarked(removed)) {
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
            }
        }

        ItemStack old = CustomSlotStorage.getPhysical(gunStack, message.slotId);
        if (!old.isEmpty() && !liberated && !virtual
                && !com.tacz.guns.util.VirtualOemAttachment.isMarked(old)) {
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }

        CustomSlotStorage.installBuiltin(gunStack, message.slotId, message.attachmentId);
        CustomSlotManager.cascadeUnloadDependents(player, gunStack);
        CustomSlotManager.cascadeUnloadConflicts(player, gunStack);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }

    private static int refitPointInSlot(ItemStack gunStack, IGun gun, String refId) {
        ItemStack stack = CustomSlotStorage.getPhysical(gunStack, refId);
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
