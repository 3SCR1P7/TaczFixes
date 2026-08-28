package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class ElectromagneticCoilEnchantment extends BaseGunEnchantment {
    public ElectromagneticCoilEnchantment() {
        super(Rarity.RARE, Config.ENCH_COIL_MAX_LEVEL::get, 6, 8, 6);
    }
}