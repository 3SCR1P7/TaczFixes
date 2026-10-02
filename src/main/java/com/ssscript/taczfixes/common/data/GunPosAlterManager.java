package com.ssscript.taczfixes.common.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GunPosAlterManager {
    private static final Map<ResourceLocation, Map<String, float[]>> RANGES = new ConcurrentHashMap<>();

    private GunPosAlterManager() {
    }

    public static void putAll(Map<ResourceLocation, Map<String, float[]>> map) {
        RANGES.clear();
        RANGES.putAll(map);
    }

    public static float[] getRange(ResourceLocation gunId, String slotKey) {
        if (gunId == null || slotKey == null) return null;
        Map<String, float[]> ranges = RANGES.get(gunId);
        if (ranges == null) {
            ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
            ranges = dataId == null ? null : RANGES.get(dataId);
        }
        return ranges == null ? null : ranges.get(slotKey);
    }

    /** 先取枪械 data 的全局范围, 再取槽定义(配件自带)的 pos_alter 范围。 */
    public static float[] getRange(net.minecraft.world.item.ItemStack gunStack, String slotKey) {
        if (slotKey == null) return null;
        com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(gunStack);
        if (gun != null) {
            float[] range = getRange(gun.getGunId(gunStack), slotKey);
            if (range != null) return range;
        }
        return CustomSlotManager.getPosAlterRange(gunStack, slotKey);
    }
}