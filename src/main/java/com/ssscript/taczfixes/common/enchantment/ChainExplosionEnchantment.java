package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.Config;

public class ChainExplosionEnchantment extends BaseGunEnchantment {
    public ChainExplosionEnchantment() {
        super(Rarity.RARE, Config.ENCH_CHAIN_EXPLOSION_MAX_LEVEL::get, 6, 8, 6);
    }
}