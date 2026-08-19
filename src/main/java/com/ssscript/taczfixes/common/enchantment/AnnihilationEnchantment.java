package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class AnnihilationEnchantment extends BaseGunEnchantment {
    public AnnihilationEnchantment() {
        super(Rarity.RARE, Config.ENCH_ANNIHILATION_MAX_LEVEL::get, 6, 8, 6);
    }
}