package com.example.taczfixes.util;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class GunsmithLibHelper {
    private static Boolean loaded = null;
    private static Method removeAimResult = null;
    private static boolean removeAimResultChecked = false;
    private static Field aimResultsField = null;
    private static boolean aimResultsFieldChecked = false;

    private static final String HOMING_BEHAVIOR_CLASS = "mod.chloeprime.gunsmithlib.common.gunpack_extension.shared.fire_control.HomingProjectileBehavior";
    private static final String FIRE_CONTROL_BEHAVIOR_CLASS = "mod.chloeprime.gunsmithlib.common.gunpack_extension.shared.fire_control.FireControlBehavior";

    public static final String TRACKING_ENABLED_KEY = "gunsmithlib:homing.enabled";
    private static final String KEY_TARGET = "gunsmithlib:homing.target";
    private static final String KEY_STOP_ON_PENETRATION = "gunsmithlib:homing.stop_on_penetration";

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("gunsmithlib");
        }
        return loaded;
    }

    public static boolean isTracking(Entity bullet) {
        if (!isLoaded()) return false;
        var data = bullet.getPersistentData();
        return data.contains(TRACKING_ENABLED_KEY) || data.contains(KEY_TARGET) || data.contains(KEY_STOP_ON_PENETRATION);
    }

    public static void disableTracking(Entity bullet) {
        if (!isLoaded()) return;
        // 制导模式：直接关闭 NBT 开关，GunsmithLib 每 tick 都会读取该键
        bullet.getPersistentData().putBoolean(TRACKING_ENABLED_KEY, false);
        // 瞬瞄模式：移除服务器端用于重瞄准的 AIM_RESULTS 表项
        removeAimResult(bullet);
    }

    @SuppressWarnings("unchecked")
    private static void removeAimResult(Entity bullet) {
        try {
            if (!removeAimResultChecked) {
                removeAimResultChecked = true;
                try {
                    Class<?> fireControlBehaviorClass = Class.forName(FIRE_CONTROL_BEHAVIOR_CLASS);
                    removeAimResult = fireControlBehaviorClass.getMethod("removeAimResult", Entity.class);
                } catch (Exception ignored) {
                }
            }
            if (removeAimResult != null) {
                removeAimResult.invoke(null, bullet);
                return;
            }
            if (!aimResultsFieldChecked) {
                aimResultsFieldChecked = true;
                try {
                    Class<?> fireControlBehaviorClass = Class.forName(FIRE_CONTROL_BEHAVIOR_CLASS);
                    aimResultsField = fireControlBehaviorClass.getDeclaredField("AIM_RESULTS");
                    aimResultsField.setAccessible(true);
                } catch (Exception ignored) {
                }
            }
            if (aimResultsField != null) {
                ThreadLocal<Map<Entity, Object>> results = (ThreadLocal<Map<Entity, Object>>) aimResultsField.get(null);
                if (results != null) {
                    results.get().remove(bullet);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
