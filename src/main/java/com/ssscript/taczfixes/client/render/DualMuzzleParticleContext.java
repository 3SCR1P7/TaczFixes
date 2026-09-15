package com.ssscript.taczfixes.client.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualMuzzleParticleContext {
    private static final ThreadLocal<Boolean> OFFHAND = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DualMuzzleParticleContext() {
    }

    public static void runOffhand(Runnable action) {
        OFFHAND.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            OFFHAND.set(Boolean.FALSE);
        }
    }

    public static boolean isOffhand() {
        return OFFHAND.get().booleanValue();
    }
}
