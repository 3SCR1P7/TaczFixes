package com.ssscript.taczfixes.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** 枪包设置保存后延迟到界面关闭再重载资源(客户端资源包 + 单人集成的数据包)。 */
public final class PendingResourceReload {
    private static boolean pending = false;

    private PendingResourceReload() {
    }

    public static void request() {
        pending = true;
    }

    /** 由客户端 tick 调用; 界面已关闭时执行重载。 */
    public static void tick() {
        if (!pending) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        pending = false;
        mc.reloadResourcePacks();
        // 枪包同时提供 data 内容: 服务端数据包需要重建仓库(重跑 RepositorySource)再重载,
        // 否则禁用/新增的枪包的 data 与创造标签页不会更新。
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            net.minecraft.server.packs.repository.PackRepository repository = server.getPackRepository();
            repository.reload();
            server.reloadResources(repository.getSelectedIds());
        }
    }
}
