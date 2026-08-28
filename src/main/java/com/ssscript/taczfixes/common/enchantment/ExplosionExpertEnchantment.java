package com.ssscript.taczfixes.common.enchantment;

import com.ssscript.taczfixes.common.register.Config;

public class ExplosionExpertEnchantment extends BaseGunEnchantment {
    public ExplosionExpertEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_EXPLOSION_EXPERT_MAX_LEVEL::get, 8, 8, 6);
    }
}