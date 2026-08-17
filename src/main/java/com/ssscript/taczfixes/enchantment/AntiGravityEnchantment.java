package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class AntiGravityEnchantment extends BaseGunEnchantment {
    public AntiGravityEnchantment() {
        super(Rarity.RARE, Config.ENCH_ANTIGRAVITY_MAX_LEVEL::get, 6, 8, 6);
    }
}