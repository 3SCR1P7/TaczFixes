package com.ssscript.taczfixes.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerMessageAutoCraftState {
    private static final ResourceLocation NONE = new ResourceLocation("taczfixes", "none");

    private final int containerId;
    private final boolean enabled;
    private final ResourceLocation recipeId;
    private final boolean enoughMaterials;
    private final float progress;
    private final boolean coolingDown;

    public ServerMessageAutoCraftState(int containerId, boolean enabled, ResourceLocation recipeId,
                                       boolean enoughMaterials, float progress, boolean coolingDown) {
        this.containerId = containerId;
        this.enabled = enabled;
        this.recipeId = recipeId == null ? NONE : recipeId;
        this.enoughMaterials = enoughMaterials;
        this.progress = progress;
        this.coolingDown = coolingDown;
    }

    public static void encode(ServerMessageAutoCraftState message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.containerId);
        buf.writeBoolean(message.enabled);
        buf.writeResourceLocation(message.recipeId);
        buf.writeBoolean(message.enoughMaterials);
        buf.writeFloat(message.progress);
        buf.writeBoolean(message.coolingDown);
    }

    public static ServerMessageAutoCraftState decode(FriendlyByteBuf buf) {
        return new ServerMessageAutoCraftState(
                buf.readVarInt(), buf.readBoolean(), buf.readResourceLocation(),
                buf.readBoolean(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(ServerMessageAutoCraftState message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.ssscript.taczfixes.client.util.GunSmithTableAutoCraftClientState.handle(
                        message.containerId, message.enabled, message.recipeId,
                        message.enoughMaterials, message.progress, message.coolingDown)));
        context.setPacketHandled(true);
    }
}
