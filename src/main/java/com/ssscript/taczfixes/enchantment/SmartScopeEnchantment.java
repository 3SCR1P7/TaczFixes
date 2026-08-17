package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class SmartScopeEnchantment extends BaseGunEnchantment {
    public SmartScopeEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_SMART_SCOPE_MAX_LEVEL::get, 20, 10, 8);
    }
}