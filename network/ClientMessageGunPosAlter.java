package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.data.CustomSlotManager;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

public class ClientMessageGunPosAlter {
    private static final float MAX_ABS = 100.0F;

    private final String slotKey;
    private final float value;

    public ClientMessageGunPosAlter(String slotKey, float value) {
        this.slotKey = slotKey;
        this.value = value;
    }

    public static void encode(ClientMessageGunPosAlter message, FriendlyByteBuf buf) {
        buf.writeUtf(message.slotKey);
        buf.writeFloat(message.value);
    }

    public static ClientMessageGunPosAlter decode(FriendlyByteBuf buf) {
        return new ClientMessageGunPosAlter(buf.readUtf(), buf.readFloat());
    }

    public static void handle(ClientMessageGunPosAlter message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handleServer(context, message));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, ClientMessageGunPosAlter message) {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (!isValidSlotKey(gunId, message.slotKey)) return;
        float clamped = Math.max(-MAX_ABS, Math.min(MAX_ABS, message.value));
        if (Float.isNaN(clamped) || Float.isInfinite(clamped)) clamped = 0.0F;
        PosAlterStorage.set(gunStack, message.slotKey, clamped);
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean isValidSlotKey(ResourceLocation gunId, String slotKey) {
        if (slotKey == null || slotKey.isEmpty()) return false;
        if (CustomSlotManager.getSlot(gunId, slotKey) != null) return true;
        try {
            AttachmentType type = AttachmentType.valueOf(slotKey.toUpperCase(Locale.US));
            return type != AttachmentType.NONE;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}