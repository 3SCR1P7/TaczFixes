package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.client.client.ClientArGate;
import com.ssscript.taczfixes.common.config.Config;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.item.ItemStack;

public final class GunSlotGlintContext {
    private static final ThreadLocal<Deque<Boolean>> STATES = new ThreadLocal<>();

    private GunSlotGlintContext() {
    }

    public static void begin(ItemStack stack) {
        boolean active = stack != null && !stack.isEmpty() && stack.isEnchanted()
                && Config.GUN_ENCHANT_GLINT_STRENGTH.get() > 0.0D;
        Deque<Boolean> states = STATES.get();
        if (states == null) {
            states = new ArrayDeque<>();
            STATES.set(states);
        }
        states.push(active);
        if (active) {
            ClientArGate.pushForcedVanilla();
        }
    }

    public static void end() {
        Deque<Boolean> states = STATES.get();
        if (states == null) {
            return;
        }
        boolean active = states.pop();
        if (states.isEmpty()) {
            STATES.remove();
        }
        if (active) {
            ClientArGate.popForcedVanilla();
        }
    }

    public static boolean isActive() {
        Deque<Boolean> states = STATES.get();
        return states != null && states.peek() != null && states.peek();
    }
}
