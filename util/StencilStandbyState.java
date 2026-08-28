package com.ssscript.taczfixes.common.util;

public final class StencilStandbyState {
    private static boolean active = false;
    private static int func = 514;
    private static int ref = 0;
    private static int mask = 255;

    private StencilStandbyState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void set(int func, int ref, int mask) {
        StencilStandbyState.func = func;
        StencilStandbyState.ref = ref;
        StencilStandbyState.mask = mask;
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    public static int getFunc() {
        return func;
    }

    public static int getRef() {
        return ref;
    }

    public static int getMask() {
        return mask;
    }
}