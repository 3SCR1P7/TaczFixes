package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.ssscript.taczfixes.client.mixin.MixinAnimationControllerAccessor;
import com.ssscript.taczfixes.client.mixin.MixinObjectAnimationRunnerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MainhandMovementAnimation {
    private static final ThreadLocal<AnimationController> BOUNDARY_CONTROLLER = new ThreadLocal<>();
    private static boolean dualLastSprinting;

    private MainhandMovementAnimation() {
    }

    public static ObjectAnimation resolvePrototype(AnimationController controller, String animationName, ObjectAnimation original, AnimationListenerSupplier listenerSupplier) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (DualMovementAnimationLibrary.isMovementClip(animationName) && (listenerSupplier instanceof BedrockAnimatedModel)) {
            BedrockGunModel bedrockGunModel = (BedrockGunModel) listenerSupplier;
            if (BOUNDARY_CONTROLLER.get() == controller) {
                ObjectAnimation boundaryFallback = DualMovementAnimationLibrary.getMainPrototypes().get(animationName);
                return original == null ? boundaryFallback : original;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (!minecraft.options.getCameraType().isFirstPerson()) {
                return original;
            }
            LocalPlayer player = minecraft.player;
            if (!DualWieldClient.isDualMovementActive(player) || OffhandDisplayManager.isActiveModel(bedrockGunModel)) {
                return original;
            }
            GunDisplayInstance mainDisplay = (GunDisplayInstance) TimelessAPI.getGunDisplay(player.getMainHandItem()).orElse(null);
            if (mainDisplay == null) {
                animationStateMachine = null;
            } else {
                animationStateMachine = mainDisplay.getAnimationStateMachine();
            }
            LuaAnimationStateMachine<GunAnimationStateContext> mainStateMachine = animationStateMachine;
            if (mainDisplay == null || mainDisplay.getGunModel() != bedrockGunModel || mainStateMachine == null || mainStateMachine.getAnimationController() != controller) {
                return original;
            }
            ObjectAnimation replacement = DualMovementAnimationLibrary.getMainPrototypes().get(animationName);
            return replacement == null ? original : replacement;
        }
        return original;
    }

    public static void resetMovementBoundary(ItemStack mainStack) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (mainStack == null || mainStack.isEmpty()) {
            return;
        }
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(mainStack).orElse(null);
        if (display == null) {
            animationStateMachine = null;
        } else {
            animationStateMachine = display.getAnimationStateMachine();
        }
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = animationStateMachine;
        if (stateMachine != null && stateMachine.isInitialized()) {
            BOUNDARY_CONTROLLER.set(stateMachine.getAnimationController());
            try {
                stateMachine.trigger("idle");
                BOUNDARY_CONTROLLER.remove();
            } catch (Throwable th) {
                BOUNDARY_CONTROLLER.remove();
                throw th;
            }
        }
    }

    /**
     * 双持时接管主手移动动画的输入: 主手动作动画(换弹/掏枪/检视等)播放期间不进入 run,
     * 等动作动画结束后先回 idle 再触发 run, 让状态机重新播放 run_start 起手动画。
     */
    public static String adjustDualMovementTrigger(String input, LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !DualWieldClient.isDualMode(player)) {
            dualLastSprinting = false;
            return input;
        }
        boolean sprinting = !player.isMovingSlowly() && player.isSprinting();
        if (sprinting) {
            if (isMainActionBusy(player)) {
                dualLastSprinting = false;
                return "idle";
            }
            if (!dualLastSprinting) {
                dualLastSprinting = true;
                return "idle";
            }
            return "run";
        }
        dualLastSprinting = false;
        return input;
    }

    private static boolean isMainActionBusy(LocalPlayer player) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator != null) {
            if (operator.getSynIsBolting()) {
                return true;
            }
            ReloadState reloadState = operator.getSynReloadState();
            if (reloadState != null && reloadState.getStateType().isReloading()) {
                return true;
            }
        }
        GunDisplayInstance display = (GunDisplayInstance) TimelessAPI.getGunDisplay(player.getMainHandItem()).orElse(null);
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display == null ? null : display.getAnimationStateMachine();
        return stateMachine != null && hasRunningActionAnimation(stateMachine.getAnimationController());
    }

    /** 控制器中是否仍有正在播放的动作类动画(换弹尾段、掏枪、检视、近战等)。 */
    public static boolean hasRunningActionAnimation(AnimationController controller) {
        if (controller == null) {
            return false;
        }
        synchronized (controller) {
            for (ObjectAnimationRunner runner : ((MixinAnimationControllerAccessor) controller).dualWield$getCurrentRunners()) {
                if (runner == null || !((MixinObjectAnimationRunnerAccessor) runner).taczfixes$isRunning()) {
                    continue;
                }
                ObjectAnimation animation = runner.getAnimation();
                if (animation != null && isActionAnimationName(animation.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isActionAnimationName(String name) {
        if (name == null) {
            return false;
        }
        return name.startsWith("reload")
                || name.startsWith("draw")
                || name.startsWith("put_away")
                || name.startsWith("bolt")
                || name.startsWith("inspect")
                || name.startsWith("melee")
                || name.startsWith("bayonet");
    }
}
