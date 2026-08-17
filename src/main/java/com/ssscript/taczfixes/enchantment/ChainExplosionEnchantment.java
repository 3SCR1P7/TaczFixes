package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class ChainExplosionEnchantment extends BaseGunEnchantment {
    public ChainExplosionEnchantment() {
        super(Rarity.RARE, Config.ENCH_CHAIN_EXPLOSION_MAX_LEVEL::get, 6, 8, 6);
    }
}