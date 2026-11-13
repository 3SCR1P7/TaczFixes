package com.ssscript.taczfixes.client.hud;

import net.neoforged.fml.ModList;

/**
 * ApricityUI 可选依赖入口。未安装时 {@link #isAvailable()} 恒为 false,
 * 所有调用方都回退到旧 JSON HUD 或原版 HUD。
 */
public final class ApricityHudSupport {

    private static final String MOD_ID = "apricityui";
    private static final String BRIDGE_CLASS =
            "com.ssscript.taczfixes.client.hud.aui.ApricityHudBridge";

    private static volatile boolean initialized;
    private static volatile ApricityHudBackend backend;

    private ApricityHudSupport() {
    }

    public static ApricityHudBackend backend() {
        if (!initialized) {
            initialize();
        }
        return backend;
    }

    private static synchronized void initialize() {
        if (initialized) return;
        backend = createBackend();
        initialized = true;
    }

    public static boolean isAvailable() {
        return backend() != null;
    }

    /** 只判断模组是否加载, 不初始化 AUI 类。 */
    public static boolean isAuiModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ApricityHudBackend createBackend() {
        if (!isAuiModLoaded()) return null;
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            Object instance = bridge.getDeclaredConstructor().newInstance();
            if (instance instanceof ApricityHudBackend hudBackend) {
                return hudBackend;
            }
        } catch (Throwable ignored) {
            // AUI 版本不兼容或类缺失: 静默回退
        }
        return null;
    }
}
