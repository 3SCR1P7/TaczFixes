package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class StabilityEnchantment extends BaseGunEnchantment {
    public StabilityEnchantment() {
        super(Rarity.RARE, Config.ENCH_STABILITY_MAX_LEVEL::get, 6, 8, 6);
    }
}