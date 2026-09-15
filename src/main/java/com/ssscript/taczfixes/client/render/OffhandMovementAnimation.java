package com.ssscript.taczfixes.client.render;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.ssscript.taczfixes.client.mixin.MixinAnimationControllerAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/OffhandMovementAnimation.class */
public final class OffhandMovementAnimation {
    private OffhandMovementAnimation() {
    }

    public static OffhandMovementAnimation install(AnimationController targetController) {
        Map<String, ObjectAnimation> replacements = DualMovementAnimationLibrary.getPrototypes();
        Map<String, ObjectAnimation> prototypes = ((MixinAnimationControllerAccessor) targetController).dualWield$getPrototypes();
        prototypes.putAll(replacements);
        return new OffhandMovementAnimation();
    }

    public void update(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, boolean actionBlocked) {
        if (stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        if (actionBlocked) {
            stateMachine.trigger("idle");
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.input == null) {
            stateMachine.trigger("idle");
            return;
        }
        if (!player.isMovingSlowly() && player.isSprinting()) {
            stateMachine.trigger("run");
        } else if (!player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01d) {
            stateMachine.trigger("walk");
        } else {
            stateMachine.trigger("idle");
        }
    }
}
