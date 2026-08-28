package com.ssscript.taczfixes.common.util;

public final class ArcanaThermalState {
    public static volatile boolean scopeViewActive;

    private ArcanaThermalState() {
    }

    public static boolean isScopeViewActive() {
        return scopeViewActive;
    }
}
