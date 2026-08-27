package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class PatienceEnchantment extends BaseGunEnchantment {
    public PatienceEnchantment() {
        super(Rarity.RARE, Config.ENCH_PATIENCE_MAX_LEVEL::get, 5, 2, 4);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}