package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.handler.GunSmithTableAutoCraftHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientMessageAutoCraft {
    private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("taczfixes", "none");

    private final int containerId;
    private final ResourceLocation recipeId;
    private final boolean enabled;

    public ClientMessageAutoCraft(int containerId, ResourceLocation recipeId, boolean enabled) {
        this.containerId = containerId;
        this.recipeId = recipeId == null ? NONE : recipeId;
        this.enabled = enabled;
    }

    public static void encode(ClientMessageAutoCraft message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.containerId);
        buf.writeResourceLocation(message.recipeId);
        buf.writeBoolean(message.enabled);
    }

    public static ClientMessageAutoCraft decode(FriendlyByteBuf buf) {
        return new ClientMessageAutoCraft(buf.readVarInt(), buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(ClientMessageAutoCraft message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            GunSmithTableAutoCraftHandler.setEnabled(player, message.containerId, message.recipeId, message.enabled);
        });
        context.setPacketHandled(true);
    }
}
