package com.ssscript.taczfixes.client.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualWieldAnimatorContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> DUAL_WIELDING = new ThreadLocal<>();

    private DualWieldAnimatorContext() {
    }

    public static void begin(boolean dualWielding) {
        int depth = DEPTH.get();
        if (depth == 0) {
            if (dualWielding) {
                DUAL_WIELDING.set(Boolean.TRUE);
            } else {
                DUAL_WIELDING.remove();
            }
        }
        DEPTH.set(depth + 1);
    }

    public static void end() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
            DUAL_WIELDING.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isDualWielding() {
        return Boolean.TRUE.equals(DUAL_WIELDING.get());
    }
}
