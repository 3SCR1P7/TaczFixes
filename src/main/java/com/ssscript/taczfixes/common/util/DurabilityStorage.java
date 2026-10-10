package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/** 枪械耐久存储与配置访问。耐久NBT缺省视为满耐久。 */
public final class DurabilityStorage {
    public static final String TAG_KEY = "TaczFixesDurability";

    private DurabilityStorage() {
    }

    public static GunTaczFixesData.DurabilityConfig config(ItemStack stack) {
        return TaczFixesDataManager.resolveDurability(stack);
    }

    public static int getMax(ItemStack stack) {
        GunTaczFixesData.DurabilityConfig cfg = config(stack);
        if (cfg == null || cfg.durability == null) {
            return 0;
        }
        return Math.max(0, cfg.durability);
    }

    public static boolean isDurableGun(ItemStack stack) {
        return getMax(stack) > 0;
    }

    public static int get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_KEY)) {
            return max;
        }
        return Math.max(0, Math.min(tag.getInt(TAG_KEY), max));
    }

    public static void set(ItemStack stack, int value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int max = getMax(stack);
        if (max <= 0) {
            return;
        }
        stack.getOrCreateTag().putInt(TAG_KEY, Math.max(0, Math.min(value, max)));
    }

    /** 消耗耐久(允许归零), 返回消耗后的剩余值。 */
    public static int consume(ItemStack stack, int amount) {
        int current = get(stack);
        int remaining = Math.max(0, current - Math.max(0, amount));
        set(stack, remaining);
        return remaining;
    }

    /** 无耐久时的动作: remove/disable/none; 未配置默认 disable。 */
    public static String damageAction(GunTaczFixesData.DurabilityConfig cfg) {
        if (cfg == null || cfg.damage_action == null) {
            return "disable";
        }
        String action = cfg.damage_action.trim().toLowerCase(Locale.ROOT);
        return switch (action) {
            case "remove", "none" -> action;
            default -> "disable";
        };
    }

    /** 材料是否在 repair_material 列表中(支持 "id" 与 "#tag")。 */
    public static boolean isRepairMaterial(ItemStack material, GunTaczFixesData.DurabilityConfig cfg) {
        if (material == null || material.isEmpty() || cfg == null
                || cfg.repair_material == null || cfg.repair_material.isEmpty()) {
            return false;
        }
        for (String entry : cfg.repair_material) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String value = entry.trim();
            if (value.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
                if (tagId != null && material.is(TagKey.create(Registries.ITEM, tagId))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null && id.equals(ForgeRegistries.ITEMS.getKey(material.getItem()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
