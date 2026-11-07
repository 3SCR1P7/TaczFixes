package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.ssscript.taczfixes.client.mixin.MixinAnimationControllerAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class OffhandMovementAnimation {
    /** 上一次更新是否处于动画/动作阻塞状态。 */
    private boolean wasBlocked;
    /** 上一次更新是否正在疾跑。 */
    private boolean lastSprinting;

    private OffhandMovementAnimation() {
    }

    public static OffhandMovementAnimation install(AnimationController targetController) {
        Map<String, ObjectAnimation> replacements = DualMovementAnimationLibrary.getOffhandPrototypes();
        Map<String, ObjectAnimation> prototypes = ((MixinAnimationControllerAccessor) targetController).dualWield$getPrototypes();
        prototypes.putAll(replacements);
        return new OffhandMovementAnimation();
    }

    public void update(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, boolean actionBlocked) {
        if (stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        if (actionBlocked || MainhandMovementAnimation.hasRunningActionAnimation(stateMachine.getAnimationController())) {
            this.wasBlocked = true;
            stateMachine.trigger("idle");
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.input == null) {
            this.wasBlocked = true;
            stateMachine.trigger("idle");
            return;
        }
        boolean sprinting = !player.isMovingSlowly() && player.isSprinting();
        boolean moving = !player.isMovingSlowly() && player.input.getMoveVector().length() > 0.01d;
        if (sprinting) {
            // 动画/动作刚结束, 或刚开始疾跑: 先回到 idle, 让状态机在下一帧重新进入 run,
            // 从而重新播放 run_start 起手动画, 而不是直接接上 run 循环。
            if (this.wasBlocked || !this.lastSprinting) {
                this.wasBlocked = false;
                this.lastSprinting = true;
                stateMachine.trigger("idle");
                return;
            }
            stateMachine.trigger("run");
        } else {
            this.wasBlocked = false;
            this.lastSprinting = false;
            if (moving) {
                stateMachine.trigger("walk");
            } else {
                stateMachine.trigger("idle");
            }
        }
    }
}
