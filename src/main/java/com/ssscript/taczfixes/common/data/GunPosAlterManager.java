package com.ssscript.taczfixes.common.data;

import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GunPosAlterManager {
    private static final Map<ResourceLocation, Map<String, float[]>> RANGES = new ConcurrentHashMap<>();
    private static final Set<AttachmentType> CONFIG_TYPES = EnumSet.of(
            AttachmentType.GRIP, AttachmentType.LASER, AttachmentType.SCOPE, AttachmentType.STOCK);

    private GunPosAlterManager() {
    }

    public static void putAll(Map<ResourceLocation, Map<String, float[]>> map) {
        RANGES.clear();
        RANGES.putAll(map);
    }

    public static float[] getRange(ResourceLocation gunId, String slotKey) {
        Map<String, float[]> ranges = lookupRanges(gunId);
        return ranges == null ? null : normalize(ranges.get(slotKey));
    }

    public static boolean hasRange(ResourceLocation gunId, String slotKey) {
        if (slotKey == null) return false;
        Map<String, float[]> ranges = lookupRanges(gunId);
        return ranges != null && ranges.containsKey(slotKey);
    }

    /** 枪械 data 和自定义槽定义优先; 都未显式定义时使用 refit 配置的默认范围。 */
    public static float[] getRange(ItemStack gunStack, String slotKey) {
        if (slotKey == null) return null;
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun != null) {
            ResourceLocation gunId = gun.getGunId(gunStack);
            if (hasRange(gunId, slotKey)) {
                return getRange(gunId, slotKey);
            }
        }
        if (CustomSlotManager.hasPosAlter(gunStack, slotKey)) {
            return normalize(CustomSlotManager.getPosAlterRange(gunStack, slotKey));
        }
        return getConfigRange(gunStack, slotKey);
    }

    private static Map<String, float[]> lookupRanges(ResourceLocation gunId) {
        if (gunId == null) return null;
        Map<String, float[]> ranges = RANGES.get(gunId);
        if (ranges != null) return ranges;
        ResourceLocation dataId = TaczFixesDataManager.resolveDataId(gunId);
        return dataId == null ? null : RANGES.get(dataId);
    }

    public static boolean isEnabled(ItemStack gunStack, String slotKey) {
        return getRange(gunStack, slotKey) != null;
    }

    private static float[] getConfigRange(ItemStack gunStack, String slotKey) {
        if (!Config.REFIT_POS_ALTER_ENABLED.get() || !isConfigEligible(gunStack, slotKey)) {
            return null;
        }
        return normalize(new float[]{
                Config.REFIT_POS_ALTER_MIN.get().floatValue(),
                Config.REFIT_POS_ALTER_MAX.get().floatValue()});
    }

    private static boolean isConfigEligible(ItemStack gunStack, String slotKey) {
        if (slotKey == null || slotKey.isEmpty()) return false;
        if (CustomSlotManager.getEntry(gunStack, slotKey) != null) return true;
        try {
            return CONFIG_TYPES.contains(AttachmentType.valueOf(slotKey.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static float[] normalize(float[] range) {
        if (range == null || range.length < 2) return null;
        float min = range[0];
        float max = range[1];
        if (!Float.isFinite(min) || !Float.isFinite(max)) return null;
        float low = Math.min(min, max);
        float high = Math.max(min, max);
        if (high - low < 1.0E-6F) return null;
        return new float[]{low, high};
    }
}
