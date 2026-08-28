package com.ssscript.taczfixes.client.util;

public final class TaczFixesClientState {
    private static boolean rejectFocusClear;

    private TaczFixesClientState() {
    }

    public static void markRejectFocusClear() {
        rejectFocusClear = true;
    }

    public static boolean consumeRejectFocusClear() {
        boolean v = rejectFocusClear;
        rejectFocusClear = false;
        return v;
    }
}