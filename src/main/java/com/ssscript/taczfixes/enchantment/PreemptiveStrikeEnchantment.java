package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class PreemptiveStrikeEnchantment extends BaseGunEnchantment {
    public PreemptiveStrikeEnchantment() {
        super(Rarity.RARE, Config.ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL::get, 6, 8, 6);
    }
}