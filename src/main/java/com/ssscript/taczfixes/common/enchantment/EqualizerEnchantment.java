package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class EqualizerEnchantment extends BaseGunEnchantment {
    public EqualizerEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_EQUALIZER_MAX_LEVEL::get, 12, 8, 6);
    }
}