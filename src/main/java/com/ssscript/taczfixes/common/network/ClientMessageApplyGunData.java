package com.ssscript.taczfixes.common.network;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.GunDataEditorHelper;
import com.ssscript.taczfixes.common.util.GunDataOverrideStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 -> 服务器: 应用编辑后的枪械 data(仅 2 级及以上权限)。 */
public class ClientMessageApplyGunData {
    private static final int MAX_TEXT = 1 << 20;

    private final ResourceLocation gunId;
    private final String fullText;

    public ClientMessageApplyGunData(ResourceLocation gunId, String fullText) {
        this.gunId = gunId;
        this.fullText = fullText;
    }

    public static void encode(ClientMessageApplyGunData msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.gunId);
        buf.writeUtf(msg.fullText, MAX_TEXT);
    }

    public static ClientMessageApplyGunData decode(FriendlyByteBuf buf) {
        return new ClientMessageApplyGunData(buf.readResourceLocation(), buf.readUtf(MAX_TEXT));
    }

    public static void handle(ClientMessageApplyGunData msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            ResourceLocation dataId = TaczFixesDataManager.resolveDataId(msg.gunId);
            GunDataEditorHelper.applyTaczFixes(dataId, msg.fullText);
            if (GunDataEditorHelper.applyByGunId(msg.gunId, msg.fullText)) {
                // 立即写入源枪包(zip/目录)并清理暂存, 不依赖后续 reload
                if (GunDataOverrideStorage.save(dataId, msg.fullText)) {
                    GunDataOverrideStorage.applyAll();
                }
            }
        });
        context.setPacketHandled(true);
    }
}
