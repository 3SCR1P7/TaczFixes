package com.ssscript.taczfixes.enchantment;

import com.ssscript.taczfixes.Config;

public class DeepLearningEnchantment extends BaseGunEnchantment {
    public DeepLearningEnchantment() {
        super(Rarity.VERY_RARE, Config.ENCH_DEEP_LEARNING_MAX_LEVEL::get, 5, 5, 4);
    }
}