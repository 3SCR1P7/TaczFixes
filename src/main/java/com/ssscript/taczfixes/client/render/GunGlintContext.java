package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.common.config.Config;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.item.ItemStack;

public final class GunGlintContext {
    private static final ThreadLocal<Deque<State>> STATES = new ThreadLocal<>();
    private static final ThreadLocal<Integer> SUPPRESS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> GLINT_PASS = new ThreadLocal<>();

    private GunGlintContext() {
    }

    public static void begin(ItemStack stack) {
        double configured = Config.GUN_ENCHANT_GLINT_STRENGTH.get();
        boolean active = stack != null && !stack.isEmpty() && stack.isEnchanted() && configured > 0.0D;
        Deque<State> states = STATES.get();
        if (states == null) {
            states = new ArrayDeque<>();
            STATES.set(states);
        }
        states.push(new State(active));
    }

    public static void end() {
        Deque<State> states = STATES.get();
        if (states == null) {
            return;
        }
        states.pop();
        if (states.isEmpty()) {
            STATES.remove();
        }
    }

    public static boolean isActive() {
        Deque<State> states = STATES.get();
        return states != null && states.peek() != null && states.peek().active;
    }

    public static void setGlintPass(boolean value) {
        if (value) {
            GLINT_PASS.set(Boolean.TRUE);
        } else {
            GLINT_PASS.remove();
        }
    }

    public static boolean isGlintPass() {
        return Boolean.TRUE.equals(GLINT_PASS.get());
    }

    public static void suppress() {
        Integer depth = SUPPRESS.get();
        SUPPRESS.set(depth == null ? 1 : depth + 1);
    }

    public static void unsuppress() {
        Integer depth = SUPPRESS.get();
        if (depth == null || depth <= 1) {
            SUPPRESS.remove();
        } else {
            SUPPRESS.set(depth - 1);
        }
    }

    public static boolean isSuppressed() {
        return SUPPRESS.get() != null;
    }

    private static final class State {
        private final boolean active;

        private State(boolean active) {
            this.active = active;
        }
    }
}
