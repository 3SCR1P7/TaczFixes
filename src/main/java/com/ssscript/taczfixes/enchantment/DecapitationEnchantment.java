package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class DecapitationEnchantment extends BaseGunEnchantment {
    public DecapitationEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_DECAPITATION_MAX_LEVEL::get, 10, 8, 6);
    }
}