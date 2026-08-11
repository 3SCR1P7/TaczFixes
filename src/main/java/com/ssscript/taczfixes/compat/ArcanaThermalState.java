package com.ssscript.taczfixes.compat;

public final class ArcanaThermalState {
    public static volatile boolean scopeViewActive;

    private ArcanaThermalState() {
    }

    public static boolean isScopeViewActive() {
        return scopeViewActive;
    }
}
