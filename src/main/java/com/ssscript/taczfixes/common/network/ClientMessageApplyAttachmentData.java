package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.util.AttachmentDataEditorHelper;
import com.ssscript.taczfixes.common.util.AttachmentDataOverrideStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 -> 服务端: 应用编辑后的配件 data(需要 2 级及以上权限)。 */
public class ClientMessageApplyAttachmentData {
    private static final int MAX_TEXT = 1 << 20;

    private final ResourceLocation attachmentId;
    private final String fullText;

    public ClientMessageApplyAttachmentData(ResourceLocation attachmentId, String fullText) {
        this.attachmentId = attachmentId;
        this.fullText = fullText;
    }

    public static void encode(ClientMessageApplyAttachmentData msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.attachmentId);
        buf.writeUtf(msg.fullText, MAX_TEXT);
    }

    public static ClientMessageApplyAttachmentData decode(FriendlyByteBuf buf) {
        return new ClientMessageApplyAttachmentData(buf.readResourceLocation(), buf.readUtf(MAX_TEXT));
    }

    public static void handle(ClientMessageApplyAttachmentData msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            ResourceLocation dataId = AttachmentDataEditorHelper.resolveDataId(msg.attachmentId);
            AttachmentDataEditorHelper.applyTaczFixes(dataId, msg.fullText);
            if (AttachmentDataOverrideStorage.save(dataId, msg.fullText)) {
                AttachmentDataOverrideStorage.applyAll();
            }
        });
        context.setPacketHandled(true);
    }
}
