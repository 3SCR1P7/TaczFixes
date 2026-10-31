package com.ssscript.taczfixes.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class ParCoolHelper {
    private static Boolean parcoolLoaded = null;
    private static Method parkourabilityGet = null;
    private static Method actionGet = null;
    private static Method isDoing = null;
    private static Method isCharging = null;
    private static Class<?> crawlClass = null;
    private static Class<?> slideClass = null;
    private static Class<?> chargeJumpClass = null;

    public static boolean isCrawling(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        return checkAction(player, crawlClass(), false);
    }

    public static boolean isSliding(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        return checkAction(player, slideClass(), false);
    }

    public static boolean isChargingJump(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        return checkAction(player, chargeJumpClass(), true);
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

    private static Class<?> chargeJumpClass() {
        if (chargeJumpClass == null) {
            try {
                chargeJumpClass = Class.forName("com.alrex.parcool.common.action.impl.ChargeJump");
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return chargeJumpClass;
    }

    private static boolean checkAction(Player player, Class<?> actionClass, boolean charging) {
        if (!isParCoolLoaded() || actionClass == null) return false;
        try {
            Object parkourability = getParkourability(player);
            if (parkourability == null) return false;
            Object action = actionGet.invoke(parkourability, actionClass);
            if (action == null) return false;
            if (charging) {
                if (isCharging == null) {
                    isCharging = actionClass.getMethod("isCharging");
                }
                return (boolean) isCharging.invoke(action);
            }
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
            if (parkourabilityGet == null || actionGet == null) {
                Class<?> parkourabilityClass = Class.forName("com.alrex.parcool.common.attachment.common.Parkourability");
                parkourabilityGet = parkourabilityClass.getMethod("get", Player.class);
                actionGet = parkourabilityClass.getMethod("get", Class.class);
            }
            return parkourabilityGet.invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }
}
