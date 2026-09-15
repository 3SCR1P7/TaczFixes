package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.AnimationListenerSupplier;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/MainhandMovementAnimation.class */
public final class MainhandMovementAnimation {
    private static final ThreadLocal<AnimationController> BOUNDARY_CONTROLLER = new ThreadLocal<>();

    private MainhandMovementAnimation() {
    }

    public static ObjectAnimation resolvePrototype(AnimationController controller, String animationName, ObjectAnimation original, AnimationListenerSupplier listenerSupplier) {
        LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
        if (DualMovementAnimationLibrary.isMovementClip(animationName) && (listenerSupplier instanceof BedrockAnimatedModel)) {
            BedrockGunModel bedrockGunModel = (BedrockGunModel) listenerSupplier;
            if (BOUNDARY_CONTROLLER.get() == controller) {
                ObjectAnimation boundaryFallback = DualMovementAnimationLibrary.getPrototypes().get(animationName);
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
            ObjectAnimation replacement = DualMovementAnimationLibrary.getPrototypes().get(animationName);
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
}
