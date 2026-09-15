package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.common.util.DualWieldEligibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Locale;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualFocusAimState.class */
public final class DualFocusAimState {
    private static final float PROGRESS_PER_TICK = 0.25f;
    private static final float OFFHAND_DROP_Y = 0.82f;
    private static final float OFFHAND_DROP_Z = 0.16f;
    private static final float OFFHAND_PITCH_DEGREES = 24.0f;
    private static final float OFFHAND_ROLL_DEGREES = -8.0f;
    private static boolean active;
    private static float previousProgress;
    private static float progress;
    private static AnimationController preservedMainAimController;
    private static boolean releasingPreservedMainAim;
    private static final ResourceLocation TOUHOU_MAID_ENTITY_ID = new ResourceLocation("touhou_little_maid", "maid");
    private static int preservedMainAimTrack = -1;

    private DualFocusAimState() {
    }

    public static boolean tick(boolean shouldBeActive) {
        boolean changed = active != shouldBeActive;
        active = shouldBeActive;
        if (changed && !shouldBeActive) {
            releasePreservedMainAim();
        }
        previousProgress = progress;
        progress = Mth.clamp(progress + (shouldBeActive ? PROGRESS_PER_TICK : -0.25f), 0.0f, 1.0f);
        return changed;
    }

    public static void resetImmediately() {
        active = false;
        previousProgress = 0.0f;
        progress = 0.0f;
        clearPreservedMainAim();
    }

    /* JADX WARN: Removed duplicated region for block: B:32:0x0078  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct code enable 'Show inconsistent code' option in preferences
    */
    public static boolean handleMainAimAnimationRun(AnimationController controller, AnimationListenerSupplier listenerSupplier, int track, String animationName) {
        if (controller == null || animationName == null) {
            return false;
        }
        String name = animationName.trim().toLowerCase(Locale.ROOT);
        if (controller == preservedMainAimController && track == preservedMainAimTrack
                && ("aim".equals(name) || "aim_start".equals(name))) {
            clearPreservedMainAim();
        }
        if (!"aim_end".equals(name) || releasingPreservedMainAim) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        boolean mainController = player != null
                && listenerSupplier instanceof BedrockAnimatedModel model
                && isCurrentMainController(player, controller, model);
        boolean aiming = mainController && active && DualWieldClient.isDualMode(player)
                && IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
        if (!aiming) {
            if (controller == preservedMainAimController && track == preservedMainAimTrack) {
                clearPreservedMainAim();
            }
            return false;
        }
        preservedMainAimController = controller;
        preservedMainAimTrack = track;
        return true;
    }

    static void releasePreservedMainAimAfterInputExit() {
        releasePreservedMainAim();
    }

    private static void releasePreservedMainAim() {
        AnimationController controller = preservedMainAimController;
        int track = preservedMainAimTrack;
        clearPreservedMainAim();
        if (controller == null || track < 0 || !controller.containPrototype("aim_end")) {
            return;
        }
        releasingPreservedMainAim = true;
        try {
            controller.runAnimation(track, "aim_end", ObjectAnimation.PlayType.PLAY_ONCE_STOP, 0.2f);
            releasingPreservedMainAim = false;
        } catch (Throwable th) {
            releasingPreservedMainAim = false;
            throw th;
        }
    }

    private static void clearPreservedMainAim() {
        preservedMainAimController = null;
        preservedMainAimTrack = -1;
    }

    private static boolean isCurrentMainController(LocalPlayer player, AnimationController controller, BedrockAnimatedModel model) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(player.getMainHandItem()).orElse(null);
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        return display != null && display.getGunModel() == model && stateMachine != null && stateMachine.getAnimationController() == controller;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isPoseVisible() {
        return active || progress > 0.0f || previousProgress > 0.0f;
    }

    public static float getProgress(float partialTick) {
        float interpolated = Mth.lerp(Mth.clamp(partialTick, 0.0f, 1.0f), previousProgress, progress);
        return interpolated * interpolated * (3.0f - (2.0f * interpolated));
    }

    public static float getFrameProgress() {
        return getProgress(Minecraft.getInstance().getFrameTime());
    }

    public static float getEntityProgress(LivingEntity entity) {
        if (entity == null || !DualWieldEligibility.isDualWielding(entity)) {
            return 0.0f;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && (entity == localPlayer || entity.getUUID().equals(localPlayer.getUUID()))) {
            return getFrameProgress();
        }
        if (!(entity instanceof Player) && !isTouhouMaid(entity)) {
            return 0.0f;
        }
        try {
            return Mth.clamp(IGunOperator.fromLivingEntity(entity).getSynAimingProgress(), 0.0f, 1.0f);
        } catch (RuntimeException e) {
            return 0.0f;
        }
    }

    private static boolean isTouhouMaid(LivingEntity entity) {
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return TOUHOU_MAID_ENTITY_ID.equals(entityId);
    }

    public static void applyFirstPersonOffhandLowReady(PoseStack poseStack, float partialTick) {
        float amount = getProgress(partialTick);
        if (poseStack == null || amount <= 0.0f) {
            return;
        }
        poseStack.translate(0.0d, OFFHAND_DROP_Y * amount, OFFHAND_DROP_Z * amount);
        poseStack.mulPose(Axis.XP.rotationDegrees(OFFHAND_PITCH_DEGREES * amount));
        poseStack.mulPose(Axis.ZP.rotationDegrees(OFFHAND_ROLL_DEGREES * amount));
    }
}
