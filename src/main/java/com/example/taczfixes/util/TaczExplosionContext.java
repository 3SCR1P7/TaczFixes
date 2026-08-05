package com.example.taczfixes.util;

public class TaczExplosionContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        DEPTH.set(DEPTH.get() - 1);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
