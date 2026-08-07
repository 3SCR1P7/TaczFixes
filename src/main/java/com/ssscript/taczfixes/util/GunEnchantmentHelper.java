package com.ssscript.taczfixes.util;

import com.ssscript.taczfixes.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 枪械附魔的工具集：白名单判断、附魔等级读取、伤害/击退/点燃倍率计算。
 */
public class GunEnchantmentHelper {
    private GunEnchantmentHelper() {
    }

    public static boolean isEnabled() {
        return Config.GUN_ENCHANTMENT_ENABLED.get();
    }

    public static boolean isEnchantAllowed(Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (key == null) {
            return false;
        }
        List<? extends String> whitelist = Config.GUN_ENCHANT_WHITELIST.get();
        return whitelist.contains(key.toString());
    }

    public static int getLevel(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty() || enchantment == null) {
            return 0;
        }
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, stack);
    }

    public static boolean hasEnchant(ItemStack stack, Enchantment enchantment) {
        return getLevel(stack, enchantment) > 0;
    }

    /** 获取射手持握物品中的指定附魔等级（枪械在主手）。 */
    public static int getLevelFromShooter(@Nullable LivingEntity shooter, Enchantment enchantment) {
        if (shooter == null) {
            return 0;
        }
        return getLevel(shooter.getMainHandItem(), enchantment);
    }

    /** 获取带附魔的枪械物品堆。若主手不是枪械则返回空。 */
    public static ItemStack getGunStack(@Nullable LivingEntity shooter) {
        if (shooter == null) {
            return ItemStack.EMPTY;
        }
        return shooter.getMainHandItem();
    }

    private static final Set<Enchantment> CACHED_WHITELIST = new HashSet<>();

    /**
     * 惰性加载白名单对应的 Enchantment 对象（用于 isEnchantable 校验时与注册表对齐）。
     */
    public static boolean isWhitelistKey(String key) {
        List<? extends String> whitelist = Config.GUN_ENCHANT_WHITELIST.get();
        return whitelist.contains(key);
    }

    // ---- 冲突忽略标记 ----

    /**
     * 当前是否处于“为枪械附魔/合成”的上下文。
     * 原版 Enchantment.isCompatibleWith 没有 ItemStack 参数，无法直接得知目标物品，
     * 因此由 EnchantmentHelper.selectEnchantment 与 AnvilMenu.createResult 在入口处根据
     * 目标物品是否为枪械来设置此标记，供冲突检查读取。
     */
    private static final ThreadLocal<Boolean> GUN_ENCHANTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setGunEnchanting(boolean flag) {
        GUN_ENCHANTING.set(flag);
    }

    public static boolean isGunEnchanting() {
        return isEnabled() && GUN_ENCHANTING.get();
    }

    // ---- 伤害计算 ----

    public static boolean isUndead(MobType type) {
        return type == MobType.UNDEAD;
    }

    public static boolean isArthropod(MobType type) {
        return type == MobType.ARTHROPOD;
    }

    public static boolean isAquatic(LivingEntity target) {
        return target.getMobType() == MobType.WATER || target.isInWater();
    }

    // ---- 枪械等级伤害 ----

    /** 读取枪械的等级 NBT（由 GunLevelHandler 写入）。 */
    public static int getGunLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return stack.getOrCreateTag().getInt("GunLevel");
    }

    /**
     * 枪械等级伤害倍率：每级 +GUN_LEVEL_DAMAGE_PER_LEVEL，不封顶。
     */
    public static float getGunLevelDamageFactor(@Nullable LivingEntity shooter) {
        int level = getGunLevel(getGunStack(shooter));
        if (level <= 0) {
            return 1.0f;
        }
        return 1.0f + (float) (Config.GUN_LEVEL_DAMAGE_PER_LEVEL.get() * level);
    }

    // ---- 激流 ----

    /**
     * 激流等级：射手处于水中/雨中/气泡中时返回附魔等级，否则 0。
     */
    public static int getRiptideLevel(@Nullable LivingEntity shooter) {
        if (shooter == null || !shooter.isInWaterRainOrBubble()) {
            return 0;
        }
        return getLevelFromShooter(shooter, Enchantments.RIPTIDE);
    }

    public static float getRiptideDamageFactor(@Nullable LivingEntity shooter) {
        int level = getRiptideLevel(shooter);
        if (level <= 0) {
            return 1.0f;
        }
        return 1.0f + (float) (Config.ENCH_RIPTIDE_DAMAGE_MULT.get() * level);
    }

    public static float getRiptideSpeedFactor(@Nullable LivingEntity shooter) {
        int level = getRiptideLevel(shooter);
        if (level <= 0) {
            return 1.0f;
        }
        return 1.0f + (float) (Config.ENCH_RIPTIDE_SPEED_MULT.get() * level);
    }

    // ---- 快速装填 ----

    /**
     * 换弹时间因子（0~1，越小越快，最低 0.1）：换弹实际耗时 = 原耗时 * 此因子。
     */
    public static float getQuickChargeTimeFactor(@Nullable LivingEntity shooter) {
        int level = getLevelFromShooter(shooter, Enchantments.QUICK_CHARGE);
        if (level <= 0) {
            return 1.0f;
        }
        double reduction = Config.ENCH_QUICK_CHARGE_TIME_REDUCTION.get() * level;
        return (float) Math.max(1.0 - reduction, 0.1);
    }

    /**
     * 换弹动画播放倍速，与换弹时间因子互为倒数，保证动画在缩短的换弹时间内播完。
     */
    public static float getQuickChargeAnimationSpeed(@Nullable LivingEntity shooter) {
        float factor = getQuickChargeTimeFactor(shooter);
        if (factor <= 0.0f) {
            return 1.0f;
        }
        return 1.0f / factor;
    }
}