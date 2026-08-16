package com.ssscript.taczfixes.network;

import com.ssscript.taczfixes.data.CustomSlotDefinition;
import com.ssscript.taczfixes.data.CustomSlotManager;
import com.ssscript.taczfixes.util.CustomSlotStorage;
import com.ssscript.taczfixes.util.LiberateCompat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageUnloadCustomSlot {
    private final String slotId;

    public ClientMessageUnloadCustomSlot(String slotId) {
        this.slotId = slotId;
    }

    public static void encode(ClientMessageUnloadCustomSlot message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotId);
    }

    public static ClientMessageUnloadCustomSlot decode(FriendlyByteBuf buf) {
        return new ClientMessageUnloadCustomSlot(buf.readUtf());
    }

    public static void handle(ClientMessageUnloadCustomSlot message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageUnloadCustomSlot message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        if (gun.hasAttachmentLock(gunStack)) return;

        ResourceLocation gunId = gun.getGunId(gunStack);
        CustomSlotDefinition def = CustomSlotManager.getSlot(gunId, message.slotId);
        if (def == null) return;

        ItemStack removed = CustomSlotStorage.unload(gunStack, message.slotId);
        if (removed.isEmpty()) return;
        CustomSlotManager.cascadeUnloadDependents(player, gunStack);
        if (!LiberateCompat.isLiberated(player)) {
            if (!player.getInventory().add(removed)) {
                player.drop(removed, false);
            }
        }
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
        player.inventoryMenu.broadcastChanges();
        com.tacz.guns.network.NetworkHandler.sendToClientPlayer(new com.tacz.guns.network.message.ServerMessageRefreshRefitScreen(), player);
    }
}
