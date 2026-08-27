package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class AbyssGazerEnchantment extends BaseGunEnchantment {
    public AbyssGazerEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_ABYSSGAZER_MAX_LEVEL::get, 10, 8, 8);
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}