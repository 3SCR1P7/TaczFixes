package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class LifeLeechEnchantment extends BaseGunEnchantment {
    public LifeLeechEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_LIFE_LEECH_MAX_LEVEL::get, 8, 8, 6);
    }
}