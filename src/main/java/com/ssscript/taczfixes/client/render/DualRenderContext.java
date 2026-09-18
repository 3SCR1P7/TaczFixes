package com.ssscript.taczfixes.client.render;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.ssscript.taczfixes.common.util.DualWieldOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
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
    private static final ThreadLocal<OffhandModelBase> MAIN_MODEL_BASE = new ThreadLocal<>();

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
        MAIN_MODEL_BASE.remove();
    }

    public static void captureMainModelBase(BedrockAnimatedModel model, Matrix4f pose) {
        if (PHASE.get() == HandPhase.MAIN && model != null && pose != null) {
            MAIN_MODEL_BASE.set(new OffhandModelBase(model, new Matrix4f(pose)));
        }
    }

    public static Matrix4f currentModelBase(BedrockAnimatedModel model) {
        OffhandModelBase base = PHASE.get() == HandPhase.MAIN ? MAIN_MODEL_BASE.get() : PHASE.get() == HandPhase.OFFHAND ? OFFHAND_MODEL_BASE.get() : null;
        return base != null && base.model == model ? new Matrix4f(base.pose) : null;
    }

    public static void captureOffhandModelBase(BedrockAnimatedModel model, Matrix4f pose) {
        if (PHASE.get() == HandPhase.OFFHAND && model != null && pose != null) {
            OFFHAND_MODEL_BASE.set(new OffhandModelBase(model, new Matrix4f(pose)));
        }
    }

    public static DualWieldOverrides.HandPos mainHandPos() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        return DualWieldOverrides.handPosRight(stack, DualWieldOverrides.DEFAULT_ON_RIGHT);
    }

    public static DualWieldOverrides.HandPos offhandHandPos() {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getOffhandItem();
        return DualWieldOverrides.handPosLeft(stack, DualWieldOverrides.DEFAULT_ON_LEFT);
    }

    public static Matrix4f resolveOffhandArmPose(BedrockAnimatedModel model, Matrix4f armPose) {
        OffhandModelBase base = OFFHAND_MODEL_BASE.get();
        if (PHASE.get() != HandPhase.OFFHAND || base == null || base.model != model) {
            return armPose;
        }
        return OffhandArmPoseResolver.resolve(model, base.pose, armPose, offhandHandPos().mirror());
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
        MAIN_MODEL_BASE.remove();
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
