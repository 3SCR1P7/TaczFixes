package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class RandomEnchantment extends BaseGunEnchantment {
    public RandomEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_RANDOM_MAX_LEVEL::get, 10, 8, 6);
    }
}