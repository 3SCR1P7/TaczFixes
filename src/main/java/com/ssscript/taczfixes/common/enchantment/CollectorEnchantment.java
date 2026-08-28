package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class CollectorEnchantment extends BaseGunEnchantment {
    public CollectorEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_COLLECTOR_MAX_LEVEL::get, 8, 8, 6);
    }
}