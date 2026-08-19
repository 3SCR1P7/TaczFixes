package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.Config;
import com.ssscript.taczfixes.common.TaczFixesMod;
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
import java.util.List;

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

    public static int getLevelFromShooter(@Nullable LivingEntity shooter, Enchantment enchantment) {
        if (shooter == null) {
            return 0;
        }
        return getLevel(shooter.getMainHandItem(), enchantment);
    }

    public static ItemStack getGunStack(@Nullable LivingEntity shooter) {
        if (shooter == null) {
            return ItemStack.EMPTY;
        }
        return shooter.getMainHandItem();
    }

    public static boolean isWhitelistKey(String key) {
        List<? extends String> whitelist = Config.GUN_ENCHANT_WHITELIST.get();
        return whitelist.contains(key);
    }

    private static final ThreadLocal<Boolean> GUN_ENCHANTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setGunEnchanting(boolean flag) {
        GUN_ENCHANTING.set(flag);
    }

    public static boolean isGunEnchanting() {
        return isEnabled() && GUN_ENCHANTING.get();
    }

    public static int getAnvilCostMultiplier(Enchantment enchantment) {
        if (enchantment == null) {
            return -1;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (key == null || !key.getNamespace().equals(TaczFixesMod.MOD_ID)) {
            return -1;
        }
        return switch (key.getPath()) {
            case "standard_ammo" -> Config.ENCH_STANDARD_AMMO_ANVIL_MULT.get();
            case "neurotoxin" -> Config.ENCH_NEUROTOXIN_ANVIL_MULT.get();
            case "chain_explosion" -> Config.ENCH_CHAIN_EXPLOSION_ANVIL_MULT.get();
            case "preemptive_strike" -> Config.ENCH_PREEMPTIVE_STRIKE_ANVIL_MULT.get();
            case "annihilation" -> Config.ENCH_ANNIHILATION_ANVIL_MULT.get();
            case "electromagnetic_coil" -> Config.ENCH_COIL_ANVIL_MULT.get();
            case "anti_gravity" -> Config.ENCH_ANTIGRAVITY_ANVIL_MULT.get();
            case "stability" -> Config.ENCH_STABILITY_ANVIL_MULT.get();
            case "overload" -> Config.ENCH_OVERLOAD_ANVIL_MULT.get();
            case "collector" -> Config.ENCH_COLLECTOR_ANVIL_MULT.get();
            case "explosion_expert" -> Config.ENCH_EXPLOSION_EXPERT_ANVIL_MULT.get();
            case "life_leech" -> Config.ENCH_LIFE_LEECH_ANVIL_MULT.get();
            case "sniper_elite" -> Config.ENCH_SNIPER_ELITE_ANVIL_MULT.get();
            case "pandora_paradox" -> Config.ENCH_PANDORA_PARADOX_ANVIL_MULT.get();
            case "smart_scope" -> Config.ENCH_SMART_SCOPE_ANVIL_MULT.get();
            case "deep_learning" -> Config.ENCH_DEEP_LEARNING_ANVIL_MULT.get();
            case "equalizer" -> Config.ENCH_EQUALIZER_ANVIL_MULT.get();
            case "random" -> Config.ENCH_RANDOM_ANVIL_MULT.get();
            case "decapitation" -> Config.ENCH_DECAPITATION_ANVIL_MULT.get();
            case "charge" -> Config.ENCH_CHARGE_ANVIL_MULT.get();
            default -> -1;
        };
    }

    public static boolean isConfiguredAnvilEnchantment(Enchantment enchantment) {
        return getAnvilCostMultiplier(enchantment) > 0;
    }

    public static boolean isUndead(MobType type) {
        return type == MobType.UNDEAD;
    }

    public static boolean isArthropod(MobType type) {
        return type == MobType.ARTHROPOD;
    }

    public static boolean isAquatic(LivingEntity target) {
        return target.getMobType() == MobType.WATER || target.isInWater();
    }

    public static int getGunLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return stack.getOrCreateTag().getInt("GunLevel");
    }

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

    public static float getQuickChargeTimeFactor(@Nullable LivingEntity shooter) {
        int level = getLevelFromShooter(shooter, Enchantments.QUICK_CHARGE);
        if (level <= 0) {
            return 1.0f;
        }
        double reduction = Config.ENCH_QUICK_CHARGE_TIME_REDUCTION.get() * level;
        return (float) Math.max(1.0 - reduction, 0.1);
    }

    public static float getQuickChargeAnimationSpeed(@Nullable LivingEntity shooter) {
        float factor = getQuickChargeTimeFactor(shooter);
        if (factor <= 0.0f) {
            return 1.0f;
        }
        return 1.0f / factor;
    }

    public static int getOverloadLevel(ItemStack stack) {
        return getLevel(stack, com.ssscript.taczfixes.common.TaczFixesMod.OVERLOAD_ENCHANTMENT.get());
    }

    public static float getEfficiencyFireRateFactor(ItemStack gun) {
        int level = getLevel(gun, Enchantments.BLOCK_EFFICIENCY);
        if (level <= 0) {
            return 1.0f;
        }
        return 1.0f + (float) (Config.ENCH_EFFICIENCY_FIRE_RATE_PERCENT_PER_LEVEL.get() / 100.0 * level);
    }

    public static float getEfficiencyBoltTimeFactor(ItemStack gun) {
        int level = getLevel(gun, Enchantments.BLOCK_EFFICIENCY);
        if (level <= 0) {
            return 1.0f;
        }
        return (float) Math.max(1.0 - Config.ENCH_EFFICIENCY_BOLT_TIME_REDUCTION_PERCENT_PER_LEVEL.get() / 100.0 * level, 0.1);
    }

    public static float getEfficiencyBoltAnimationSpeed(@Nullable LivingEntity shooter) {
        float factor = getEfficiencyBoltTimeFactor(getGunStack(shooter));
        if (factor >= 1.0f) {
            return 1.0f;
        }
        return 1.0f / factor;
    }

    public static float getCoilSpeedFactor(ItemStack stack) {
        int level = getLevel(stack, com.ssscript.taczfixes.common.TaczFixesMod.ELECTROMAGNETIC_COIL_ENCHANTMENT.get());
        if (level <= 0) {
            return 1.0f;
        }
        return 1.0f + (float) (Config.ENCH_COIL_SPEED_PERCENT.get() / 100.0 * level);
    }

    public static float getCoilInaccuracyFactor(ItemStack stack) {
        int level = getLevel(stack, com.ssscript.taczfixes.common.TaczFixesMod.ELECTROMAGNETIC_COIL_ENCHANTMENT.get());
        if (level <= 0) {
            return 1.0f;
        }
        return (float) Math.max(1.0 - Config.ENCH_COIL_SPREAD_REDUCTION_PERCENT.get() / 100.0 * level, 0.0);
    }
}