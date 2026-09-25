package com.ssscript.taczfixes.common.network;

import com.google.gson.JsonElement;
import com.ssscript.taczfixes.common.data.AttachmentTaczFixesManager;
import com.ssscript.taczfixes.common.data.CustomSlotDefinition;
import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.VirtualAttachments;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 虚拟配件模式下从候选栏安装配件: slotKey 为标准槽类型名(如 "scope")或自定义槽 id。
 * 不消耗背包、不返还旧配件; 装上的是带虚拟 OEM 标记的配件。
 */
public class ClientMessageInstallVirtualAttachment {
    private final String slotKey;
    private final ResourceLocation attachmentId;

    public ClientMessageInstallVirtualAttachment(String slotKey, ResourceLocation attachmentId) {
        this.slotKey = slotKey;
        this.attachmentId = attachmentId;
    }

    public static void encode(ClientMessageInstallVirtualAttachment message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotKey);
        buf.writeResourceLocation(message.attachmentId);
    }

    public static ClientMessageInstallVirtualAttachment decode(FriendlyByteBuf buf) {
        return new ClientMessageInstallVirtualAttachment(buf.readUtf(), buf.readResourceLocation());
    }

    public static void handle(ClientMessageInstallVirtualAttachment message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageInstallVirtualAttachment message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        if (!VirtualAttachments.isActive(player)) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ItemStack built = CustomSlotManager.buildBuiltinItem(message.attachmentId);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(built);
        if (attachment == null) return;
        AttachmentType realType = attachment.getType(built);
        ResourceLocation gunId = gun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, message.slotKey);

        if (def != null) {
            boolean builtin = CustomSlotManager.isBuiltinCandidate(def, message.attachmentId);
            if (!builtin && !CustomSlotManager.matchesSlot(def, gunId, message.attachmentId, realType)) return;
            if (!VirtualAttachments.allowsForSlot(gunStack, message.slotKey, message.attachmentId)) return;
            if (!CustomSlotManager.isDependenceMet(gunId, gunStack, def)) return;

            Set<String> toUnload = new LinkedHashSet<>();
            for (java.util.Map.Entry<String, JsonElement> conflictEntry : def.getConflict().entrySet()) {
                if (CustomSlotManager.satisfies(gunId, gunStack, conflictEntry.getKey(), conflictEntry.getValue())) {
                    toUnload.add(conflictEntry.getKey());
                }
            }
            for (java.util.Map.Entry<String, CustomSlotDefinition> entry
                    : CustomSlotManager.getSlots(gunId).entrySet()) {
                if (entry.getKey().equals(message.slotKey)) continue;
                JsonElement cond = entry.getValue().getConflict().get(message.slotKey);
                if (cond != null && CustomSlotManager.satisfies(gunId, gunStack, entry.getKey(), cond)) {
                    toUnload.add(entry.getKey());
                }
            }
            Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
            if (total != null) {
                int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
                int delta = -AttachmentTaczFixesManager.getRefitPointConsume(
                        CustomSlotStorage.getPhysical(gunStack, message.slotKey));
                for (String conflictId : toUnload) {
                    delta -= refitPointInSlot(gunStack, gun, conflictId);
                }
                if (!builtin) {
                    delta += AttachmentTaczFixesManager.getRefitPointConsume(built);
                }
                if (used + delta > total) {
                    return;
                }
            }
            for (String conflictId : toUnload) {
                if (CustomSlotManager.getSlot(gunId, conflictId) != null) {
                    CustomSlotStorage.unload(gunStack, conflictId);
                } else {
                    unloadStandard(gunStack, gun, conflictId);
                }
            }
            if (builtin) {
                CustomSlotStorage.installBuiltin(gunStack, message.slotKey, message.attachmentId);
            } else {
                CustomSlotStorage.install(gunStack, message.slotKey, built);
            }
        } else {
            AttachmentType type;
            try {
                type = AttachmentType.valueOf(message.slotKey.toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                return;
            }
            if (type == AttachmentType.NONE || realType != type) return;
            if (!gun.allowAttachmentType(gunStack, type)) return;
            if (!gun.allowAttachment(gunStack, built)) return;
            ItemStack old = gun.getAttachment(gunStack, type);
            Integer total = TaczFixesDataManager.getGunRefitPoint(gunStack);
            if (total != null) {
                int used = AttachmentTaczFixesManager.getRefitPointUsed(gunStack);
                int oldConsume = old.isEmpty() ? 0 : AttachmentTaczFixesManager.getRefitPointConsume(old);
                int add = AttachmentTaczFixesManager.getRefitPointConsume(built);
                if (used + add > total + oldConsume) {
                    return;
                }
            }
            ResourceLocation previousId = gun.getAttachmentId(gunStack, type);
            gun.installAttachment(gunStack, built);
            com.ssscript.taczfixes.common.compat.ArcanaSkillBridge.triggerChangeAttachment(player,
                    com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(previousId) ? null : previousId.toString(),
                    message.attachmentId.toString());
            if (type == AttachmentType.EXTENDED_MAG) {
                gun.dropAllAmmo(player, gunStack);
            }
        }

        CustomSlotManager.cascadeUnloadDependents(player, gunStack);
        CustomSlotManager.cascadeUnloadConflicts(player, gunStack);
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }

    private static void unloadStandard(ItemStack gunStack, IGun gun, String refId) {
        try {
            AttachmentType type = AttachmentType.valueOf(refId.toUpperCase(Locale.US));
            gun.unloadAttachment(gunStack, type);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static int refitPointInSlot(ItemStack gunStack, IGun gun, String refId) {
        ItemStack stack = CustomSlotStorage.getPhysical(gunStack, refId);
        if (stack.isEmpty()) {
            try {
                AttachmentType type = AttachmentType.valueOf(refId.toUpperCase(Locale.US));
                stack = gun.getAttachment(gunStack, type);
            } catch (IllegalArgumentException ex) {
                return 0;
            }
        }
        return AttachmentTaczFixesManager.getRefitPointConsume(stack);
    }
}
