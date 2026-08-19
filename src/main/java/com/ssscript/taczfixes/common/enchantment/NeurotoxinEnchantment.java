package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class NeurotoxinEnchantment extends BaseGunEnchantment {
    public NeurotoxinEnchantment() {
        super(Rarity.RARE, Config.ENCH_NEUROTOXIN_MAX_LEVEL::get, 6, 8, 6);
    }
}