package com.ssscript.taczfixes.compat;

/**
 * 记录 Arcana 是否正在渲染热成像瞄具视野（离屏 Scope 渲染通道）。
 * 由 MixinArcanaScopeRenderState 在每帧渲染时更新。
 */
public final class ArcanaThermalState {
    public static volatile boolean scopeViewActive;

    private ArcanaThermalState() {
    }

    public static boolean isScopeViewActive() {
        return scopeViewActive;
    }
}
