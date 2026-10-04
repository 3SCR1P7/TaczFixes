package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.ssscript.taczfixes.common.util.GunColorStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/** 客户端改装界面把调色板同步到服务端。 slotId 为空表示枪械本体, 否则为自定义槽中的配件。 */
public class ClientMessageGunColor {
    private final String slotId;
    private final int[] targets;

    public ClientMessageGunColor(String slotId, int[] targets) {
        this.slotId = slotId == null ? "" : slotId;
        this.targets = targets == null ? new int[0] : targets;
    }

    public static void encode(ClientMessageGunColor message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
        buf.writeVarIntArray(message.targets);
    }

    public static ClientMessageGunColor decode(FriendlyByteBuf buf) {
        return new ClientMessageGunColor(buf.readUtf(), buf.readVarIntArray());
    }

    public static void handle(ClientMessageGunColor message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageGunColor message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunStack) == null) return;
        int[] targets = message.targets;
        if (targets.length > GunColorStorage.MAX_CLUSTERS) {
            targets = Arrays.copyOf(targets, GunColorStorage.MAX_CLUSTERS);
        }
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (message.slotId.isEmpty()) {
            GunColorStorage.set(gunStack, targets);
        } else if (message.slotId.startsWith("t:")) {
            AttachmentType type;
            try {
                type = AttachmentType.valueOf(message.slotId.substring(2).toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                return;
            }
            ItemStack item = gun == null ? ItemStack.EMPTY : gun.getAttachment(gunStack, type);
            if (item.isEmpty()) return;
            GunColorStorage.set(item, targets);
            CompoundTag tag = gunStack.getOrCreateTag();
            CompoundTag attachmentTag = new CompoundTag();
            item.save(attachmentTag);
            tag.put(com.tacz.guns.api.item.nbt.GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name(), attachmentTag);
        } else {
            ItemStack item = CustomSlotStorage.getPhysical(gunStack, message.slotId);
            if (item.isEmpty()) return;
            GunColorStorage.set(item, targets);
            CompoundTag tag = gunStack.getOrCreateTag();
            CompoundTag slots = tag.contains(CustomSlotStorage.TAG_KEY, 10)
                    ? tag.getCompound(CustomSlotStorage.TAG_KEY) : new CompoundTag();
            slots.put(message.slotId, item.save(new CompoundTag()));
            tag.put(CustomSlotStorage.TAG_KEY, slots);
        }
        player.inventoryMenu.broadcastChanges();
    }
}
