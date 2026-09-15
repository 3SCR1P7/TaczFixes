package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.client.render.DualRenderContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualMuzzleFlashState.class */
public final class DualMuzzleFlashState {
    private static DualRenderContext.HandPhase latestHand = DualRenderContext.HandPhase.NONE;
    private static long latestTimestamp = -1;
    private static final Vector3f MAIN_MUZZLE = new Vector3f();
    private static final Vector3f OFFHAND_MUZZLE = new Vector3f();
    private static boolean mainMuzzleValid;
    private static boolean offhandMuzzleValid;

    private DualMuzzleFlashState() {
    }

    public static void record(DualRenderContext.HandPhase hand) {
        long now = System.currentTimeMillis();
        if (hand != DualRenderContext.HandPhase.MAIN && hand != DualRenderContext.HandPhase.OFFHAND) {
            return;
        }
        latestHand = hand;
        latestTimestamp = now;
    }

    public static boolean shouldSuppress(DualRenderContext.HandPhase renderingHand) {
        return renderingHand != latestHand || latestTimestamp < 0 || System.currentTimeMillis() - latestTimestamp > 75;
    }

    public static void captureMuzzle(DualRenderContext.HandPhase hand, Vector3f offset) {
        if (offset == null || !Float.isFinite(offset.x()) || !Float.isFinite(offset.y()) || !Float.isFinite(offset.z())) {
            invalidateMuzzle(hand);
            return;
        }
        if (hand == DualRenderContext.HandPhase.MAIN) {
            MAIN_MUZZLE.set(offset);
            mainMuzzleValid = true;
        } else if (hand == DualRenderContext.HandPhase.OFFHAND) {
            OFFHAND_MUZZLE.set(offset);
            offhandMuzzleValid = true;
        }
    }

    public static void invalidateMuzzle(DualRenderContext.HandPhase hand) {
        if (hand == DualRenderContext.HandPhase.MAIN) {
            mainMuzzleValid = false;
        } else if (hand == DualRenderContext.HandPhase.OFFHAND) {
            offhandMuzzleValid = false;
        }
    }

    @Nullable
    public static Vector3f copyMuzzle(boolean offhand) {
        if (offhand) {
            if (offhandMuzzleValid) {
                return new Vector3f(OFFHAND_MUZZLE);
            }
            return null;
        }
        if (mainMuzzleValid) {
            return new Vector3f(MAIN_MUZZLE);
        }
        return null;
    }

    public static void clearMuzzlePositions() {
        mainMuzzleValid = false;
        offhandMuzzleValid = false;
        latestHand = DualRenderContext.HandPhase.NONE;
        latestTimestamp = -1L;
    }
}
