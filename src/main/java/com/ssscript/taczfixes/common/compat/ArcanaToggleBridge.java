package com.ssscript.taczfixes.common.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.item.ItemStack;

/**
 * 双持兼容: 副手枪械的 Arcana 激光/手电开关。
 * 主手为物品时 Arcana 的服务端开关只会读取主手, 因此这里直接调用 Arcana 自己的
 * 状态读写方法切换副手枪械的 NBT 状态 (服务端修改后会自动同步客户端)。
 */
public final class ArcanaToggleBridge {

    private static boolean initialized;
    private static Object instance;
    private static Method laserGet;
    private static Method laserSet;
    private static Method flashlightGet;
    private static Method flashlightSet;

    private ArcanaToggleBridge() {
    }

    public static void toggleLaser(ItemStack gun) {
        init();
        if (instance == null || gun == null || gun.isEmpty()) return;
        try {
            boolean current = (Boolean) laserGet.invoke(instance, gun);
            laserSet.invoke(instance, gun, !current);
        } catch (Throwable ignored) {
        }
    }

    public static void toggleFlashlight(ItemStack gun) {
        init();
        if (instance == null || gun == null || gun.isEmpty()) return;
        try {
            boolean current = (Boolean) flashlightGet.invoke(instance, gun);
            flashlightSet.invoke(instance, gun, !current);
        } catch (Throwable ignored) {
        }
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> holder = Class.forName("group.taczexpands.dist.wDqHZ7qY");
            Field singleton = holder.getField("HFrxsScC");
            instance = singleton.get(null);
            laserGet = holder.getMethod("YroxkNPW", ItemStack.class);
            laserSet = holder.getMethod("oApcCRCR", ItemStack.class, boolean.class);
            flashlightGet = holder.getMethod("y4ac6dDI", ItemStack.class);
            flashlightSet = holder.getMethod("CNNjkrXp", ItemStack.class, boolean.class);
        } catch (Throwable ignored) {
            instance = null;
        }
    }
}
