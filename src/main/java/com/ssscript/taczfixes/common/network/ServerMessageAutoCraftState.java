package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import com.ssscript.taczfixes.common.util.DistExecutor;

import java.util.function.Supplier;

public class ServerMessageAutoCraftState {
    private static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("taczfixes", "none");

    private final int containerId;
    private final boolean enabled;
    private final ResourceLocation recipeId;
    private final boolean enoughMaterials;
    private final float progress;
    private final boolean coolingDown;
    private final boolean powerPaused;
    private final int energy;
    private final int capacity;

    public ServerMessageAutoCraftState(int containerId, boolean enabled, ResourceLocation recipeId,
                                       boolean enoughMaterials, float progress, boolean coolingDown,
                                       boolean powerPaused, int energy, int capacity) {
        this.containerId = containerId;
        this.enabled = enabled;
        this.recipeId = recipeId == null ? NONE : recipeId;
        this.enoughMaterials = enoughMaterials;
        this.progress = progress;
        this.coolingDown = coolingDown;
        this.powerPaused = powerPaused;
        this.energy = Math.max(0, energy);
        this.capacity = Math.max(0, capacity);
    }

    public static void encode(ServerMessageAutoCraftState message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.containerId);
        buf.writeBoolean(message.enabled);
        buf.writeResourceLocation(message.recipeId);
        buf.writeBoolean(message.enoughMaterials);
        buf.writeFloat(message.progress);
        buf.writeBoolean(message.coolingDown);
        buf.writeBoolean(message.powerPaused);
        buf.writeVarInt(message.energy);
        buf.writeVarInt(message.capacity);
    }

    public static ServerMessageAutoCraftState decode(FriendlyByteBuf buf) {
        return new ServerMessageAutoCraftState(
                buf.readVarInt(), buf.readBoolean(), buf.readResourceLocation(),
                buf.readBoolean(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ServerMessageAutoCraftState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.util.GunSmithTableAutoCraftClientState.handle(
                        message.containerId, message.enabled, message.recipeId,
                        message.enoughMaterials, message.progress, message.coolingDown,
                        message.powerPaused, message.energy, message.capacity)));
        context.setPacketHandled(true);
    }
}
