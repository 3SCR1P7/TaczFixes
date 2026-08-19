package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class OverloadEnchantment extends BaseGunEnchantment {
    public OverloadEnchantment() {
        super(Rarity.RARE, Config.ENCH_OVERLOAD_MAX_LEVEL::get, 6, 8, 6);
    }
}