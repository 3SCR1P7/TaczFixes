package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class FocusedAmmoEnchantment extends BaseGunEnchantment {
    public FocusedAmmoEnchantment() {
        super(Rarity.RARE, Config.ENCH_FOCUSED_AMMO_MAX_LEVEL::get, 5, 2, 4);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}