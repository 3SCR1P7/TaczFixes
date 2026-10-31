package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/** ParCool 3.4.3.3 stamina bridge (reflection, no hard dependency). */
public final class ParCoolStaminaHelper {

    public static final ThreadLocal<Boolean> MIRRORING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static Method getStamina;
    private static Method getValue;
    private static Method getMax;
    private static Method consume;
    private static Method recover;

    private ParCoolStaminaHelper() {
    }

    public static boolean isMirroring() {
        return MIRRORING.get();
    }

    public static boolean isLoaded() {
        return ParCoolHelper.isParCoolLoaded();
    }

    public static void mirror(Player player, float value, float max, boolean exhausted) {
        if (player == null || !player.level().isClientSide()) return;
        try {
            Class<?> localPlayerClass = Class.forName("net.minecraft.client.player.LocalPlayer");
            if (!localPlayerClass.isInstance(player)) return;
            if (getStamina == null) {
                Class<?> localStamina = Class.forName("com.alrex.parcool.common.attachment.client.LocalStamina");
                getStamina = localStamina.getMethod("get", localPlayerClass);
                getValue = localStamina.getMethod("getValue", localPlayerClass);
                getMax = localStamina.getMethod("getMax", localPlayerClass);
                consume = localStamina.getMethod("consume", localPlayerClass, int.class);
                recover = localStamina.getMethod("recover", localPlayerClass, int.class);
            }
            Object stamina = getStamina.invoke(null, player);
            if (stamina == null) return;
            int parcoolMax = (int) getMax.invoke(stamina, player);
            if (parcoolMax <= 0) return;
            float ratio = max <= 0f ? 0f : Math.max(0f, Math.min(1f, value / max));
            int target = Math.round(ratio * parcoolMax);
            int current = (int) getValue.invoke(stamina, player);
            int delta = target - current;
            if (delta == 0) return;
            MIRRORING.set(Boolean.TRUE);
            try {
                if (delta > 0) {
                    consume.invoke(stamina, player, delta);
                } else {
                    recover.invoke(stamina, player, -delta);
                }
            } finally {
                MIRRORING.set(Boolean.FALSE);
            }
        } catch (Exception ignored) {
        }
    }
}
