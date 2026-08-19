package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class DecapitationEnchantment extends BaseGunEnchantment {
    public DecapitationEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_DECAPITATION_MAX_LEVEL::get, 10, 8, 6);
    }
}