package com.ssscript.taczfixes.client.render;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DualMuzzleHandScope {
    private static final long RECENT_SHOT_WINDOW_MS = 500L;
    private static final long SIMULTANEOUS_SHOT_TOLERANCE_MS = 50L;
    private static volatile long mainShootTimestamp = -1L;
    private static volatile long offhandShootTimestamp = -1L;

    private DualMuzzleHandScope() {
    }

    public static void record(DualRenderContext.HandPhase hand) {
        long now = System.currentTimeMillis();
        if (hand == DualRenderContext.HandPhase.MAIN) {
            mainShootTimestamp = now;
        } else if (hand == DualRenderContext.HandPhase.OFFHAND) {
            offhandShootTimestamp = now;
        }
    }

    public static boolean shouldCancel(DualRenderContext.HandPhase renderingHand) {
        if (renderingHand != DualRenderContext.HandPhase.MAIN && renderingHand != DualRenderContext.HandPhase.OFFHAND) {
            return false;
        }
        boolean mainRender = renderingHand == DualRenderContext.HandPhase.MAIN;
        long ownTimestamp = mainRender ? mainShootTimestamp : offhandShootTimestamp;
        long otherTimestamp = mainRender ? offhandShootTimestamp : mainShootTimestamp;
        if (otherTimestamp < 0L || System.currentTimeMillis() - otherTimestamp > RECENT_SHOT_WINDOW_MS) {
            return false;
        }
        if (ownTimestamp < 0L) {
            return true;
        }
        return otherTimestamp > ownTimestamp + SIMULTANEOUS_SHOT_TOLERANCE_MS;
    }
}
