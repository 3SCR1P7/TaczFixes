package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.world.item.ItemStack;

/**
 * 全部枪械附魔; 各自只有数值差异, 合并为一个文件便于维护。
 * 注册见 {@code TaczFixesMod}。
 */
public final class GunEnchantments {
    private GunEnchantments() {
    }

    public static class Overload extends BaseGunEnchantment {
        public Overload() {
            super(Rarity.RARE, Config.ENCH_OVERLOAD_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class Annihilation extends BaseGunEnchantment {
        public Annihilation() {
            super(Rarity.RARE, Config.ENCH_ANNIHILATION_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class Stability extends BaseGunEnchantment {
        public Stability() {
            super(Rarity.RARE, Config.ENCH_STABILITY_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class AntiGravity extends BaseGunEnchantment {
        public AntiGravity() {
            super(Rarity.RARE, Config.ENCH_ANTIGRAVITY_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class ElectromagneticCoil extends BaseGunEnchantment {
        public ElectromagneticCoil() {
            super(Rarity.RARE, Config.ENCH_COIL_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class StandardAmmo extends BaseGunEnchantment {
        public StandardAmmo() {
            super(Rarity.RARE, Config.ENCH_STANDARD_AMMO_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class Neurotoxin extends BaseGunEnchantment {
        public Neurotoxin() {
            super(Rarity.RARE, Config.ENCH_NEUROTOXIN_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class ChainExplosion extends BaseGunEnchantment {
        public ChainExplosion() {
            super(Rarity.RARE, Config.ENCH_CHAIN_EXPLOSION_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class PreemptiveStrike extends BaseGunEnchantment {
        public PreemptiveStrike() {
            super(Rarity.RARE, Config.ENCH_PREEMPTIVE_STRIKE_MAX_LEVEL::get, 6, 8, 6);
        }
    }

    public static class Collector extends BaseGunEnchantment {
        public Collector() {
            super(Rarity.VERY_RARE, Config.ENCH_COLLECTOR_MAX_LEVEL::get, 8, 8, 6);
        }
    }

    public static class ExplosionExpert extends BaseGunEnchantment {
        public ExplosionExpert() {
            super(Rarity.VERY_RARE, Config.ENCH_EXPLOSION_EXPERT_MAX_LEVEL::get, 8, 8, 6);
        }
    }

    public static class LifeLeech extends BaseGunEnchantment {
        public LifeLeech() {
            super(Rarity.VERY_RARE, Config.ENCH_LIFE_LEECH_MAX_LEVEL::get, 8, 8, 6);
        }
    }

    public static class SniperElite extends BaseGunEnchantment {
        public SniperElite() {
            super(Rarity.VERY_RARE, Config.ENCH_SNIPER_ELITE_MAX_LEVEL::get, 8, 8, 6);
        }
    }

    public static class PandoraParadox extends BaseGunEnchantment {
        public PandoraParadox() {
            super(Rarity.VERY_RARE, Config.ENCH_PANDORA_PARADOX_MAX_LEVEL::get, 8, 8, 6);
        }
    }

    public static class SmartScope extends BaseGunEnchantment {
        public SmartScope() {
            super(Rarity.VERY_RARE, Config.ENCH_SMART_SCOPE_MAX_LEVEL::get, 20, 10, 8);
        }
    }

    public static class DeepLearning extends BaseGunEnchantment {
        public DeepLearning() {
            super(Rarity.VERY_RARE, Config.ENCH_DEEP_LEARNING_MAX_LEVEL::get, 5, 5, 4);
        }
    }

    public static class Equalizer extends BaseGunEnchantment {
        public Equalizer() {
            super(Rarity.VERY_RARE, Config.ENCH_EQUALIZER_MAX_LEVEL::get, 12, 8, 6);
        }
    }

    public static class Random extends BaseGunEnchantment {
        public Random() {
            super(Rarity.VERY_RARE, Config.ENCH_RANDOM_MAX_LEVEL::get, 10, 8, 6);
        }
    }

    public static class Decapitation extends BaseGunEnchantment {
        public Decapitation() {
            super(Rarity.VERY_RARE, Config.ENCH_DECAPITATION_MAX_LEVEL::get, 10, 8, 6);
        }
    }

    public static class Charge extends BaseGunEnchantment {
        public Charge() {
            super(Rarity.VERY_RARE, Config.ENCH_CHARGE_MAX_LEVEL::get, 5, 5, 4);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    public static class AbyssGazer extends BaseGunEnchantment {
        public AbyssGazer() {
            super(Rarity.VERY_RARE, Config.ENCH_ABYSSGAZER_MAX_LEVEL::get, 10, 8, 8);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    public static class DoubleShot extends BaseGunEnchantment {
        public DoubleShot() {
            super(Rarity.RARE, Config.ENCH_DOUBLE_SHOT_MAX_LEVEL::get, 5, 2, 4);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    public static class FocusedAmmo extends BaseGunEnchantment {
        public FocusedAmmo() {
            super(Rarity.RARE, Config.ENCH_FOCUSED_AMMO_MAX_LEVEL::get, 5, 2, 4);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    public static class ArcanaEden extends BaseGunEnchantment {
        public ArcanaEden() {
            super(Rarity.VERY_RARE, Config.ENCH_ARCANA_EDEN_MAX_LEVEL::get, 6, 4, 6);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    public static class Patience extends BaseGunEnchantment {
        public Patience() {
            super(Rarity.RARE, Config.ENCH_PATIENCE_MAX_LEVEL::get, 5, 2, 4);
        }

        @Override
        public boolean isTradeable() {
            return false;
        }
    }

    /** 猩红终焉: 命中目标后持续发红, 射手死亡时目标一同死亡。 */
    public static class ScarletDeath extends BaseGunEnchantment {
        public ScarletDeath() {
            super(Rarity.VERY_RARE, Config.ENCH_SCARLET_DEATH_MAX_LEVEL::get, 10, 8, 8);
        }
    }

    /** 常凪: 命中后冻结目标 floor(伤害)×等级 tick。 */
    public static class Tokonagi extends BaseGunEnchantment {
        public Tokonagi() {
            super(Rarity.VERY_RARE, Config.ENCH_TOKONAGI_MAX_LEVEL::get, 10, 8, 8);
        }
    }

    /** 余烬: 击杀时引发带火焰、破坏方块的爆炸。 */
    public static class Ember extends BaseGunEnchantment {
        public Ember() {
            super(Rarity.VERY_RARE, Config.ENCH_EMBER_MAX_LEVEL::get, 6, 6, 6);
        }
    }

    /** 蓄势待发: 只有蓄力枪可附魔; 蓄力时受到的伤害减少。 */
    public static class Poised extends BaseGunEnchantment {
        public Poised() {
            super(Rarity.VERY_RARE, Config.ENCH_POISED_MAX_LEVEL::get, 8, 8, 6);
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return super.canApplyAtEnchantingTable(stack) && GunEnchantmentUtil.hasChargeFeature(stack);
        }
    }

    /** 封焰时刻: 只有过热枪可附魔; 伤害/射速随过热变化并点燃目标。 */
    public static class SealFlare extends BaseGunEnchantment {
        public SealFlare() {
            super(Rarity.VERY_RARE, Config.ENCH_SEAL_FLARE_MAX_LEVEL::get, 8, 8, 6);
        }

        @Override
        public boolean canApplyAtEnchantingTable(ItemStack stack) {
            return super.canApplyAtEnchantingTable(stack) && GunEnchantmentUtil.hasHeatFeature(stack);
        }
    }
}
