package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class ChargeEnchantment extends BaseGunEnchantment {
    public ChargeEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_CHARGE_MAX_LEVEL::get, 5, 5, 4);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}