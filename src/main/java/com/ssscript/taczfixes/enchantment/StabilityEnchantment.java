package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class StabilityEnchantment extends BaseGunEnchantment {
    public StabilityEnchantment() {
        super(Rarity.RARE, Config.ENCH_STABILITY_MAX_LEVEL::get, 6, 8, 6);
    }
}