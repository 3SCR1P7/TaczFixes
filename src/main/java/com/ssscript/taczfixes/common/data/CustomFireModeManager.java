package com.ssscript.taczfixes.common.data;

import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 每把枪自定义开火模式(运行时状态存储, id -> 配置)。 */
public class CustomFireModeManager {
    private static final Map<ResourceLocation, Map<String, GunTaczFixesData.CustomFireModeConfig>> GUN_MODES
            = new ConcurrentHashMap<>();

    private CustomFireModeManager() {
    }

    public static void put(ResourceLocation dataId, Map<String, GunTaczFixesData.CustomFireModeConfig> modes) {
        if (dataId == null) return;
        if (modes == null || modes.isEmpty()) {
            GUN_MODES.remove(dataId);
        } else {
            GUN_MODES.put(dataId, modes);
        }
    }

    public static Map<String, GunTaczFixesData.CustomFireModeConfig> get(ResourceLocation dataId) {
        Map<String, GunTaczFixesData.CustomFireModeConfig> modes = GUN_MODES.get(dataId);
        return modes == null ? Collections.emptyMap() : modes;
    }

    public static Set<String> ids(ResourceLocation dataId) {
        return get(dataId).keySet();
    }

    public static boolean hasCustomMode(ResourceLocation dataId, String id) {
        return id != null && get(dataId).containsKey(id);
    }

    public static GunTaczFixesData.CustomFireModeConfig mode(ResourceLocation dataId, String id) {
        return get(dataId).get(id);
    }

    /** 射击/切换流程内标记当前激活的自定义模式(作用域, 无枚举键冲突)。 */
    private static final ThreadLocal<GunTaczFixesData.CustomFireModeConfig> ACTIVE = new ThreadLocal<>();

    public static void setActive(GunTaczFixesData.CustomFireModeConfig cfg) {
        ACTIVE.set(cfg);
    }

    public static GunTaczFixesData.CustomFireModeConfig active() {
        return ACTIVE.get();
    }

    public static void resetActive() {
        ACTIVE.remove();
    }

    /** 规范化激活: 手持枪 NBT 含自定义模式 id 时按 id 激活, 否则清空。 */
    public static void activateFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            resetActive();
            return;
        }
        CompoundTag tag = stack.getTag();
        String id = tag == null ? null : tag.getString("TaczFixesCustomFireMode");
        if (id == null || id.isEmpty()) {
            resetActive();
            return;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun == null ? null : gun.getGunId(stack);
        ResourceLocation dataId = gunId == null ? null : TaczFixesDataManager.resolveDataId(gunId);
        if (dataId == null) {
            resetActive();
            return;
        }
        setActive(mode(dataId, id));
    }
}
