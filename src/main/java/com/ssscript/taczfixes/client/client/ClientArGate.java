package com.ssscript.taczfixes.client.client;

public final class ClientArGate {
    private static final ThreadLocal<Integer> FORCED_VANILLA_DEPTH = ThreadLocal.withInitial(() -> 0);

    private ClientArGate() {
    }

    public static void pushForcedVanilla() {
        FORCED_VANILLA_DEPTH.set(FORCED_VANILLA_DEPTH.get() + 1);
    }

    public static void popForcedVanilla() {
        int depth = FORCED_VANILLA_DEPTH.get() - 1;
        if (depth <= 0) {
            FORCED_VANILLA_DEPTH.remove();
        } else {
            FORCED_VANILLA_DEPTH.set(depth);
        }
    }

    public static boolean isForcedVanilla() {
        return FORCED_VANILLA_DEPTH.get() > 0;
    }
}
