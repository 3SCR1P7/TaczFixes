package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class OverloadEnchantment extends BaseGunEnchantment {
    public OverloadEnchantment() {
        super(Rarity.RARE, Config.ENCH_OVERLOAD_MAX_LEVEL::get, 6, 8, 6);
    }
}