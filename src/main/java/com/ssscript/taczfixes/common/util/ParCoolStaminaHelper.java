package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/** ParCool 耐力读写(反射, 无硬依赖)。 */
public final class ParCoolStaminaHelper {

    private static Method getStamina;
    private static Method setValue;
    private static Method getMax;
    private static Method setExhaustion;

    private ParCoolStaminaHelper() {
    }

    public static boolean isLoaded() {
        return ParCoolHelper.isParCoolLoaded();
    }

    /** 把本模组耐力按比例写入 ParCool 耐力, 并按本模组力竭状态设置 ParCool 力竭。 */
    public static void mirror(Player player, float value, float max, boolean exhausted) {
        Object stamina = get(player);
        if (stamina == null) return;
        try {
            int parcoolMax = (int) getMax.invoke(stamina);
            if (parcoolMax <= 0) return;
            float ratio = max <= 0f ? 0f : Math.max(0f, Math.min(1f, value / max));
            int mapped = Math.round(ratio * parcoolMax);
            setValue.invoke(stamina, mapped);
            setExhaustion.invoke(stamina, exhausted);
        } catch (Exception ignored) {
        }
    }

    private static Object get(Player player) {
        try {
            if (getStamina == null) {
                Class<?> clazz = Class.forName("com.alrex.parcool.common.capability.IStamina");
                getStamina = clazz.getMethod("get", Player.class);
                setValue = clazz.getMethod("set", int.class);
                getMax = clazz.getMethod("getActualMaxStamina");
                setExhaustion = clazz.getMethod("setExhaustion", boolean.class);
            }
            return getStamina.invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }
}
