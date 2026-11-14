package com.ssscript.taczfixes.common.compat;

import net.minecraft.world.entity.LivingEntity;

/**
 * 记录 YSM 手持物品层当前正在渲染的实体与副手渲染状态, 用于在同一帧内避免副手枪械被重复渲染。
 * 与 Forge 1.20.1 版本行为一致。
 */
public final class YsmHeldLayerRenderState {
    private static final ThreadLocal<Frame> CURRENT_FRAME = new ThreadLocal<>();

    private YsmHeldLayerRenderState() {
    }

    public static void prepareForEntry() {
        if (CURRENT_FRAME.get() != null) {
            CURRENT_FRAME.remove();
            YsmCompatibilityDiagnostics.reportStaleFrameRecovery();
        }
    }

    public static void begin(LivingEntity entity) {
        CURRENT_FRAME.set(new Frame(entity));
    }

    public static void end() {
        CURRENT_FRAME.remove();
    }

    public static boolean isOffhandRendered(LivingEntity entity) {
        Frame frame = getFrameFor(entity);
        return frame != null && frame.offhandRendered;
    }

    public static void markOffhandRendered(LivingEntity entity) {
        Frame frame = getFrameFor(entity);
        if (frame != null) {
            frame.offhandRendered = true;
        }
    }

    private static Frame getFrameFor(LivingEntity entity) {
        Frame frame = CURRENT_FRAME.get();
        if (frame != null && frame.entity != entity) {
            CURRENT_FRAME.remove();
            YsmCompatibilityDiagnostics.reportFrameEntityMismatch();
            return null;
        }
        return frame;
    }

    private static final class Frame {
        private final LivingEntity entity;
        private boolean offhandRendered;

        private Frame(LivingEntity entity) {
            this.entity = entity;
        }
    }
}
