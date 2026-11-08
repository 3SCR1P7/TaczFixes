package com.ssscript.taczfixes.client.client;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.ssscript.taczfixes.client.mixin.MixinAnimationControllerAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Offhand dual-wield movement animation controller.
 *
 * <p>Unlike the main hand (which resolves prototypes per animation trigger), the offhand movement
 * prototypes are written into the animation controller's prototype map, so the gun's original
 * movement clips are backed up and restored when the applied preset changes. This is required for
 * the per-gun {@code custom} preset, which must keep the gun's own movement animations.
 */
public final class OffhandMovementAnimation {
    /** 上一次更新是否处于动画/动作阻塞状态。 */
    private boolean wasBlocked;
    /** 上一次更新是否正在疾跑。 */
    private boolean lastSprinting;
    /** The gun stack this instance belongs to (movement preset is resolved per gun). */
    private ItemStack gunStack = ItemStack.EMPTY;
    /** Backed-up original movement prototypes (only clips that existed before our replacement). */
    private Map<String, ObjectAnimation> originalPrototypes = Map.of();
    /** Last applied movement-animation preset; preset changes at runtime refresh the prototypes. */
    private String appliedPreset;
    /** Controller the prototypes were applied to; a different one re-applies the preset. */
    private AnimationController controller;

    private OffhandMovementAnimation() {
    }

    public static OffhandMovementAnimation install(AnimationController targetController, ItemStack gunStack) {
        OffhandMovementAnimation animation = new OffhandMovementAnimation();
        animation.gunStack = gunStack == null || gunStack.isEmpty() ? ItemStack.EMPTY : gunStack.copy();
        animation.controller = targetController;
        if (targetController != null) {
            Map<String, ObjectAnimation> prototypes =
                    ((MixinAnimationControllerAccessor) targetController).dualWield$getPrototypes();
            Map<String, ObjectAnimation> backup = new HashMap<>();
            for (String name : DualMovementAnimationLibrary.movementClipNames()) {
                ObjectAnimation original = prototypes.get(name);
                if (original != null) {
                    backup.put(name, original);
                }
            }
            animation.originalPrototypes = Map.copyOf(backup);
        }
        animation.applyPreset(DualMovementAnimationLibrary.resolvePreset(animation.gunStack));
        return animation;
    }

    /** Restores the gun's original movement prototypes, then applies the preset replacement set. */
    private void applyPreset(String preset) {
        if (this.controller != null) {
            Map<String, ObjectAnimation> prototypes =
                    ((MixinAnimationControllerAccessor) this.controller).dualWield$getPrototypes();
            for (String name : DualMovementAnimationLibrary.movementClipNames()) {
                ObjectAnimation original = this.originalPrototypes.get(name);
                if (original != null) {
                    prototypes.put(name, original);
                } else {
                    prototypes.remove(name);
                }
            }
            if (!DualMovementAnimationLibrary.PRESET_CUSTOM.equals(preset)) {
                prototypes.putAll(DualMovementAnimationLibrary.getOffhandPrototypes(this.gunStack));
            }
        }
        this.appliedPreset = preset;
    }

    private void refreshPreset(AnimationController currentController) {
        if (currentController != this.controller) {
            this.controller = currentController;
            this.applyPreset(DualMovementAnimationLibrary.resolvePreset(this.gunStack));
            return;
        }
        String preset = DualMovementAnimationLibrary.resolvePreset(this.gunStack);
        if (!preset.equals(this.appliedPreset)) {
            this.applyPreset(preset);
        }
    }

    public void update(LuaAnimationStateMachine<GunAnimationStateContext> stateMachine, boolean actionBlocked) {
        if (stateMachine == null || !stateMachine.isInitialized()) {
            return;
        }
        AnimationController currentController = stateMachine.getAnimationController();
        if (currentController != null) {
            this.refreshPreset(currentController);
        }
        if (actionBlocked || MainhandMovementAnimation.hasRunningActionAnimation(currentController)) {
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
