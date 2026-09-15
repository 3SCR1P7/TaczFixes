package com.ssscript.taczfixes.client.render;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualRenderContext.class */
public final class DualRenderContext {
    private static final ThreadLocal<HandPhase> PHASE = ThreadLocal.withInitial(() -> {
        return HandPhase.NONE;
    });
    private static final ThreadLocal<OffhandModelBase> OFFHAND_MODEL_BASE = new ThreadLocal<>();

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualRenderContext$HandPhase.class */
    public enum HandPhase {
        NONE,
        MAIN,
        OFFHAND
    }

    private DualRenderContext() {
    }

    public static HandPhase getPhase() {
        return PHASE.get();
    }

    public static void setPhase(HandPhase phase) {
        PHASE.set(phase);
        OFFHAND_MODEL_BASE.remove();
    }

    public static void captureOffhandModelBase(BedrockAnimatedModel model, Matrix4f pose) {
        if (PHASE.get() == HandPhase.OFFHAND && model != null && pose != null) {
            OFFHAND_MODEL_BASE.set(new OffhandModelBase(model, new Matrix4f(pose)));
        }
    }

    public static Matrix4f resolveOffhandArmPose(BedrockAnimatedModel model, Matrix4f rightArmPose) {
        OffhandModelBase base = OFFHAND_MODEL_BASE.get();
        if (PHASE.get() != HandPhase.OFFHAND || base == null || base.model != model) {
            return rightArmPose;
        }
        return OffhandArmPoseResolver.resolve(model, base.pose, rightArmPose);
    }

    public static Matrix4f resolveOffhandSupportArmPose(BedrockAnimatedModel model, Matrix4f supportArmPose) {
        OffhandModelBase base = OFFHAND_MODEL_BASE.get();
        if (PHASE.get() != HandPhase.OFFHAND || base == null || base.model != model) {
            return supportArmPose;
        }
        return OffhandArmPoseResolver.resolveSupportManualAction(model, base.pose, supportArmPose);
    }

    public static void clear() {
        PHASE.remove();
        OFFHAND_MODEL_BASE.remove();
    }

    /* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualRenderContext$OffhandModelBase.class */
    private static final class OffhandModelBase {
        private final BedrockAnimatedModel model;
        private final Matrix4f pose;

        private OffhandModelBase(BedrockAnimatedModel model, Matrix4f pose) {
            this.model = model;
            this.pose = pose;
        }
    }
}
