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

    public static boolean isPending() {
        return pending;
    }

    /**
     * 主菜单下没有“世界内界面关闭”这个时机: 立即重载并清掉待处理标记。
     * 标记必须在这里清掉, 否则进入世界(界面变 null)时会再重载一次。
     */
    public static void reloadNow() {
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        if (!reloadClient(mc)) {
            return;
        }
        reloadIntegratedServer(mc);
    }

    /** 由客户端 tick 调用; 界面已关闭时执行重载。 */
    public static void tick() {
        if (!pending) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        pending = false;
        if (!reloadClient(mc)) {
            return;
        }
        reloadIntegratedServer(mc);
    }

    private static boolean reloadClient(Minecraft mc) {
        // NeoForge 21.1 在发现损坏的资源包/模组 jar 时会直接抛出异常(fml.modloading.brokenresources),
        // 进而让客户端崩溃。这里兜住: 重载失败只记录日志, 不把整个游戏带崩。
        try {
            mc.reloadResourcePacks();
            return true;
        } catch (Throwable failed) {
            com.ssscript.taczfixes.TaczFixesMod.LOGGER.warn(
                    "Resource reload failed after gun pack change: {}", failed.toString());
            return false;
        }
    }

    private static void reloadIntegratedServer(Minecraft mc) {
        // 枪包同时提供 data 内容: 服务端数据包需要重建仓库(重跑 RepositorySource)再重载,
        // 否则禁用/新增的枪包的 data 与创造标签页不会更新。
        try {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                net.minecraft.server.packs.repository.PackRepository repository = server.getPackRepository();
                repository.reload();
                server.reloadResources(repository.getSelectedIds());
            }
        } catch (Throwable failed) {
            com.ssscript.taczfixes.TaczFixesMod.LOGGER.warn(
                    "Data pack reload failed after gun pack change: {}", failed.toString());
        }
    }
}
