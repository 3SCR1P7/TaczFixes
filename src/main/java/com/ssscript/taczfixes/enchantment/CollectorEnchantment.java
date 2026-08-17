package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class CollectorEnchantment extends BaseGunEnchantment {
    public CollectorEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_COLLECTOR_MAX_LEVEL::get, 8, 8, 6);
    }
}