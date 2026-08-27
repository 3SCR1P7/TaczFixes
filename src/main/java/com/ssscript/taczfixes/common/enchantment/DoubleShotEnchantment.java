package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class DoubleShotEnchantment extends BaseGunEnchantment {
    public DoubleShotEnchantment() {
        super(Rarity.RARE, Config.ENCH_DOUBLE_SHOT_MAX_LEVEL::get, 5, 2, 4);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}