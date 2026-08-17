package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class PandoraParadoxEnchantment extends BaseGunEnchantment {
    public PandoraParadoxEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_PANDORA_PARADOX_MAX_LEVEL::get, 8, 8, 6);
    }
}