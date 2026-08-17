package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class RandomEnchantment extends BaseGunEnchantment {
    public RandomEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_RANDOM_MAX_LEVEL::get, 10, 8, 6);
    }
}