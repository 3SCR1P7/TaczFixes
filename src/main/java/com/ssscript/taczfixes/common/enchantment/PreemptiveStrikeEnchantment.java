package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class PreemptiveStrikeEnchantment extends BaseGunEnchantment {
    public PreemptiveStrikeEnchantment() {
        super(Rarity.RARE, Config.ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL::get, 6, 8, 6);
    }
}