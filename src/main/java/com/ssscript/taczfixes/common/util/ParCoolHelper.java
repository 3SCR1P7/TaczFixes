package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public class ParCoolHelper {
    private static Boolean parcoolLoaded = null;
    private static Method parkourabilityGet = null;
    private static Method getAction = null;
    private static Method isDoing = null;
    private static Class<?> crawlClass = null;
    private static Class<?> slideClass = null;

    public static boolean isCrawling(LivingEntity entity) {
        if (!isParCoolLoaded()) return false;
        if (!(entity instanceof Player player)) return false;
        return checkAction(player, crawlClass());
    }

    /** ParCool 滑铲(Slide)动作是否进行中。 */
    public static boolean isSliding(LivingEntity entity) {
        if (!isParCoolLoaded()) return false;
        if (!(entity instanceof Player player)) return false;
        return checkAction(player, slideClass());
    }

    public static boolean isParCoolLoaded() {
        if (parcoolLoaded == null) {
            parcoolLoaded = ModList.get().isLoaded("parcool");
        }
        return parcoolLoaded;
    }

    private static Class<?> crawlClass() {
        if (crawlClass == null) {
            try {
                crawlClass = Class.forName("com.alrex.parcool.common.action.impl.Crawl");
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return crawlClass;
    }

    private static Class<?> slideClass() {
        if (slideClass == null) {
            try {
                slideClass = Class.forName("com.alrex.parcool.common.action.impl.Slide");
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return slideClass;
    }

    private static boolean checkAction(Player player, Class<?> actionClass) {
        if (actionClass == null) return false;
        try {
            Object parkourability = getParkourability(player);
            if (parkourability == null) return false;
            Object action = getAction.invoke(parkourability, actionClass);
            if (action == null) return false;
            if (isDoing == null) {
                isDoing = Class.forName("com.alrex.parcool.common.action.Action").getMethod("isDoing");
            }
            return (boolean) isDoing.invoke(action);
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getParkourability(Player player) {
        try {
            if (parkourabilityGet == null || getAction == null) {
                Class<?> parkourabilityClass = Class.forName("com.alrex.parcool.common.capability.Parkourability");
                parkourabilityGet = parkourabilityClass.getMethod("get", Player.class);
                getAction = parkourabilityClass.getMethod("get", Class.class);
            }
            return parkourabilityGet.invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }
}
