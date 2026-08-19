package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.util.function.IntSupplier;

public abstract class BaseGunEnchantment extends Enchantment {
    private final IntSupplier maxLevel;
    private final int minCostBase;
    private final int minCostStep;
    private final int maxCostStep;

    protected BaseGunEnchantment(Rarity rarity, IntSupplier maxLevel,
                                 int minCostBase, int minCostStep, int maxCostStep) {
        super(rarity, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.maxLevel = maxLevel;
        this.minCostBase = minCostBase;
        this.minCostStep = minCostStep;
        this.maxCostStep = maxCostStep;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel.getAsInt();
    }

    @Override
    public int getMinCost(int level) {
        return minCostBase + level * minCostStep;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + level * maxCostStep;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return false;
        }
        if (!(stack.getItem() instanceof IGun)) {
            return false;
        }
        return GunEnchantmentHelper.isEnchantAllowed(this);
    }
}