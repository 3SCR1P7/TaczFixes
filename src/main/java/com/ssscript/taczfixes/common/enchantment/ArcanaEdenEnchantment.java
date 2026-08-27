package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class ArcanaEdenEnchantment extends BaseGunEnchantment {
    public ArcanaEdenEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_ARCANA_EDEN_MAX_LEVEL::get, 6, 4, 6);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}