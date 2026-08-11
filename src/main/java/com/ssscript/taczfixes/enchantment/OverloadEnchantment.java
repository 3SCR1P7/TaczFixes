package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class OverloadEnchantment extends Enchantment {
    public OverloadEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return Config.ENCH_OVERLOAD_MAX_LEVEL.get();
    }

    @Override
    public int getMinCost(int level) {
        return 6 + level * 8;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + level * 6;
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