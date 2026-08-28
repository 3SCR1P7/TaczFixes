package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class SniperEliteEnchantment extends BaseGunEnchantment {
    public SniperEliteEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_SNIPER_ELITE_MAX_LEVEL::get, 8, 8, 6);
    }
}