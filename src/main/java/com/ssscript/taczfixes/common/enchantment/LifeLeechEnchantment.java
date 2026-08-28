package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class LifeLeechEnchantment extends BaseGunEnchantment {
    public LifeLeechEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_LIFE_LEECH_MAX_LEVEL::get, 8, 8, 6);
    }
}