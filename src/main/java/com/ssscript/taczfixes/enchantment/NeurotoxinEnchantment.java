package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class NeurotoxinEnchantment extends BaseGunEnchantment {
    public NeurotoxinEnchantment() {
        super(Rarity.RARE, Config.ENCH_NEUROTOXIN_MAX_LEVEL::get, 6, 8, 6);
    }
}