package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class StandardAmmoEnchantment extends BaseGunEnchantment {
    public StandardAmmoEnchantment() {
        super(Rarity.RARE, Config.ENCH_STANDARD_AMMO_MAX_LEVEL::get, 6, 8, 6);
    }
}