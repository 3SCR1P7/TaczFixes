package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.util.DecapitationHelper;
import com.ssscript.taczfixes.common.util.GunEnchantmentHelper;
import com.ssscript.taczfixes.common.util.GunEnchantmentUtil;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.network.ServerMessageTokonagiFreeze;
import com.ssscript.taczfixes.common.network.ServerMessageTokonagiFreezeEntity;
import net.minecraftforge.network.PacketDistributor;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.ModDamageTypes;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.ExplodeUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GunEnchantmentHandler {
    private static final UUID LOYALTY_RANGE_UUID = UUID.fromString("4a6e9c9f-8b3e-4f5e-9f0a-1b2c3d4e5f01");
    private static final UUID LOYALTY_ANGLE_UUID = UUID.fromString("4a6e9c9f-8b3e-4f5e-9f0a-1b2c3d4e5f02");

    private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();

    /** 潘多拉悖论: 伤害加成倍率上限(生命值越低加成越高, 最多 1024 倍)。 */
    private static final double PANDORA_MAX_DAMAGE_MULTIPLIER = 1024.0D;

    // ---------------------------------------------------------------- 新增五附魔的运行时状态
    /** 猩红终焉: 死亡提示用的伤害类型。 */
    private static final ResourceLocation CARDIAC_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "cardiac_arrest");
    /** 凝渊者秒杀使用的伤害类型(死亡提示: 正在凝视深渊)。 */
    public static final ResourceLocation ABYSS_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "abyss_gaze");

    /** 被猩红终焉标记: 目标 UUID -> 射手 UUID。 */
    private static final Map<UUID, UUID> CARDIAC_TARGETS = new ConcurrentHashMap<>();
    /** 被常凪冻结: 目标 UUID -> 冻结状态。 */
    private static final Map<UUID, Freeze> FROZEN = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onGunHurt(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        applySealFlareDamage(event, shooter, target);
        int power = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter, Enchantments.POWER_ARROWS);
        int smite = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter, Enchantments.SMITE);
        int bane = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter, Enchantments.BANE_OF_ARTHROPODS);
        int impaling = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter, Enchantments.IMPALING);

        float mult = 1.0F;
        if (power > 0) {
            mult += Config.ENCH_POWER_BULLET_MULT.get().floatValue() * power;
        }
        if (smite > 0 && GunEnchantmentHelper.isUndead(target.getMobType())) {
            mult += Config.ENCH_SMITE_BULLET_MULT.get().floatValue() * smite;
        }
        if (bane > 0 && GunEnchantmentHelper.isArthropod(target.getMobType())) {
            mult += Config.ENCH_BANE_BULLET_MULT.get().floatValue() * bane;
        }
        if (impaling > 0 && GunEnchantmentHelper.isAquatic(target)) {
            mult += Config.ENCH_IMPALING_BULLET_MULT.get().floatValue() * impaling;
        }
        if (mult > 1.0F) {
            event.setBaseAmount(event.getBaseAmount() * mult);
        }
    }

    @SubscribeEvent
    public void onGunHurtPost(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        applyScarletAndTokonagi(event, shooter, target);
        if (GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter, Enchantments.CHANNELING) <= 0) {
            return;
        }
        if (target.isAlive() && rollChanneling(target)) {
            strikeLightning(shooter, target);
        }
    }

    @SubscribeEvent
    public void onGunHurtCoil(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter,
                TaczFixesMod.ELECTROMAGNETIC_COIL_ENCHANTMENT.get());
        if (level <= 0 || !target.isAlive()) {
            return;
        }
        double chancePercent = Config.ENCH_COIL_LIGHTNING_CHANCE_PERCENT.get();
        if (chancePercent >= 100.0 || shooter.getRandom().nextDouble() < chancePercent / 100.0) {
            float damage = (float) (Config.ENCH_COIL_LIGHTNING_DAMAGE_PER_LEVEL.get() * level);
            Holder<DamageType> lightningType = target.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.LIGHTNING_BOLT);
            DamageSource lightningSource = new DamageSource(lightningType, shooter, shooter);
            target.hurt(lightningSource, damage);
        }
    }

    @SubscribeEvent
    public void onGunHurtFortune(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        if (gun.isEmpty() || !(gun.getItem() instanceof IGun)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(gun, Enchantments.BLOCK_FORTUNE);
        if (level <= 0 || event.isHeadShot()) {
            return;
        }
        double chancePercent = Config.ENCH_FORTUNE_HEADSHOT_CHANCE_PERCENT_PER_LEVEL.get() * level;
        if (chancePercent >= 100.0 || shooter.getRandom().nextDouble() < chancePercent / 100.0) {
            event.setHeadshot(true);
        }
    }

    @SubscribeEvent
    public void onGunHurtPreemptiveStrike(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.PREEMPTIVE_STRIKE_ENCHANTMENT.get());
        if (level <= 0 || gun.isEmpty() || !(gun.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        if (gunItem.useInventoryAmmo(gun)) {
            return;
        }
        ResourceLocation gunId = event.getGunId();
        if (gunId == null) {
            return;
        }
        if (!Objects.equals(gunId, gunItem.getGunId(gun))) {
            return;
        }
        int magCapacity = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData()))
                .orElse(0);
        if (magCapacity <= 0) {
            return;
        }
        int currentAmmo = gunItem.getCurrentAmmoCount(gun);
        double ratio = (double) currentAmmo / magCapacity;
        double percent = Config.ENCH_PREEMPTIVE_STRIKE_DAMAGE_PERCENT_PER_LEVEL.get() * level * ratio;
        if (percent <= 0) {
            return;
        }
        event.setBaseAmount(event.getBaseAmount() * (1.0F + (float) (percent / 100.0)));
    }

    @SubscribeEvent
    public void onGunHurtCollector(EntityHurtByGunEvent.Pre event) {
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.COLLECTOR_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        long otherCount = 0L;
        for (var enchantment : EnchantmentHelper.getEnchantments(gun).keySet()) {
            if (enchantment != TaczFixesMod.COLLECTOR_ENCHANTMENT.get()) otherCount++;
        }
        if (otherCount <= 0) {
            return;
        }
        double percent = Config.ENCH_COLLECTOR_DAMAGE_PERCENT_PER_LEVEL.get() * level * otherCount;
        event.setBaseAmount(event.getBaseAmount() * (1.0F + (float) (percent / 100.0)));
    }

    @SubscribeEvent
    public void onGunHurtNeurotoxin(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        int level = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter,
                TaczFixesMod.NEUROTOXIN_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double chancePercent = Config.ENCH_NEUROTOXIN_TRIGGER_CHANCE_PERCENT_PER_LEVEL.get() * level;
        if (chancePercent < 100.0 && shooter.getRandom().nextDouble() >= chancePercent / 100.0) {
            return;
        }
        int duration = Config.ENCH_NEUROTOXIN_DURATION_TICKS.get();
        boostEffect(target, MobEffects.MOVEMENT_SLOWDOWN, duration);
        boostEffect(target, MobEffects.BLINDNESS, duration);
        boostEffect(target, MobEffects.POISON, duration);
    }

    private void boostEffect(LivingEntity target, MobEffect effect, int duration) {
        MobEffectInstance existing = target.getEffect(effect);
        int amplifier = (existing == null) ? 0 : existing.getAmplifier() + 1;
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
    }

    @SubscribeEvent
    public void onGunHurtChainExplosion(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        Entity bullet = event.getBullet();
        if (bullet == null || !(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter,
                TaczFixesMod.CHAIN_EXPLOSION_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        if (!rollChainExplosion(shooter, level)) {
            return;
        }
        float radius = randomInRange(shooter, Config.ENCH_CHAIN_EXPLOSION_RADIUS_MIN.get().floatValue(),
                (float) (Config.ENCH_CHAIN_EXPLOSION_RADIUS_SCALE_PER_LEVEL.get() * level));
        float damage = randomInRange(shooter, Config.ENCH_CHAIN_EXPLOSION_DAMAGE_BASE.get().floatValue(),
                (float) (Config.ENCH_CHAIN_EXPLOSION_DAMAGE_SCALE_PER_LEVEL.get() * level));
        ExplodeUtil.createExplosion(shooter, bullet, damage, radius, false, false, target.position());
    }

    @SubscribeEvent
    public void onAmmoBlockChainExplosion(AmmoHitBlockEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        EntityKineticBullet bullet = event.getAmmo();
        if (bullet == null || !(bullet.getOwner() instanceof LivingEntity shooter)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevelFromShooter(bullet.getGunId(), shooter,
                TaczFixesMod.CHAIN_EXPLOSION_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        if (!rollChainExplosion(shooter, level)) {
            return;
        }
        float radius = randomInRange(shooter, Config.ENCH_CHAIN_EXPLOSION_RADIUS_MIN.get().floatValue(),
                (float) (Config.ENCH_CHAIN_EXPLOSION_RADIUS_SCALE_PER_LEVEL.get() * level));
        float damage = randomInRange(shooter, Config.ENCH_CHAIN_EXPLOSION_DAMAGE_BASE.get().floatValue(),
                (float) (Config.ENCH_CHAIN_EXPLOSION_DAMAGE_SCALE_PER_LEVEL.get() * level));
        ExplodeUtil.createExplosion(shooter, bullet, damage, radius, false, false,
                event.getHitResult().getLocation());
    }

    private boolean rollChainExplosion(LivingEntity shooter, int level) {
        double chancePercent = Config.ENCH_CHAIN_EXPLOSION_BASE_CHANCE_PERCENT.get()
                + Config.ENCH_CHAIN_EXPLOSION_CHANCE_PERCENT_PER_LEVEL.get() * level;
        return chancePercent >= 100.0 || shooter.getRandom().nextDouble() < chancePercent / 100.0;
    }

    private float randomInRange(LivingEntity shooter, float min, float max) {
        if (max <= min) {
            return min;
        }
        return min + shooter.getRandom().nextFloat() * (max - min);
    }

    private static final Map<UUID, Float> PENDING_BULLET_DAMAGE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onLivingHurtAnnihilation(LivingHurtEvent event) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        if (!event.getSource().is(ModDamageTypes.BULLETS_TAG)) {
            return;
        }
        float dealt = event.getAmount();
        if (dealt > 0.0F) {
            PENDING_BULLET_DAMAGE.merge(target.getUUID(), dealt, Float::sum);
        }
    }

    @SubscribeEvent
    public void onLivingDeathAnnihilation(LivingDeathEvent event) {
        PENDING_BULLET_DAMAGE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onGunHurtAnnihilation(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.ANNIHILATION_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        Float dealt = PENDING_BULLET_DAMAGE.remove(target.getUUID());
        if (dealt == null || dealt <= 0.0F) {
            return;
        }
        float extra = dealt * (float) (Config.ENCH_ANNIHILATION_DAMAGE_PERCENT.get() / 100.0) * level;
        if (extra <= 0.0F) {
            return;
        }
        Entity bullet = event.getBullet();
        if (bullet == null) {
            return;
        }
        Holder<DamageType> voidType = bullet.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.BULLET_VOID);
        DamageSource voidSource = new DamageSource(voidType, shooter, shooter);
        target.hurt(voidSource, extra);
    }

    @SubscribeEvent
    public void onGunKillLifeLeech(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        LivingEntity killed = event.getKilledEntity();
        if (shooter == null || killed == null) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.LIFE_LEECH_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        float heal = killed.getMaxHealth() * (float) (Config.ENCH_LIFE_LEECH_HEAL_PERCENT_PER_LEVEL.get() / 100.0) * level;
        if (heal > 0.0F) {
            shooter.heal(heal);
        }
    }

    @SubscribeEvent
    public void onGunHurtSniperElite(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null || !event.isHeadShot()) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.SNIPER_ELITE_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double percent = Config.ENCH_SNIPER_ELITE_HEADSHOT_PERCENT_PER_LEVEL.get() * level;
        event.setBaseAmount(event.getBaseAmount() * (1.0F + (float) (percent / 100.0)));
    }

    @SubscribeEvent
    public void onGunFirePandora(GunFireEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        if (shooter == null) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(event.getGunItemStack(),
                TaczFixesMod.PANDORA_PARADOX_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        float current = shooter.getHealth();
        float max = shooter.getMaxHealth();
        if (current <= 0.0F || max <= 0.0F) {
            return;
        }
        if (shooter.getRandom().nextFloat() < Math.min(2.0F * current / max, 1.0F)) {
            shooter.setHealth(current / 2.0F);
        }
    }

    @SubscribeEvent
    public void onGunHurtPandora(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.PANDORA_PARADOX_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        float current = shooter.getHealth();
        float max = shooter.getMaxHealth();
        if (current <= 0.0F || max <= 0.0F) {
            return;
        }
        double multiplier = Math.min((double) max / current, PANDORA_MAX_DAMAGE_MULTIPLIER);
        event.setBaseAmount(event.getBaseAmount() * (float) multiplier);
    }

    @SubscribeEvent
    public void onGunHurtDeepLearning(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.DEEP_LEARNING_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        int gunLevel = GunEnchantmentHelper.getGunLevel(gun);
        if (gunLevel <= 0) {
            return;
        }
        double percent = Config.ENCH_DEEP_LEARNING_DAMAGE_PERCENT_PER_GUN_LEVEL_PER_LEVEL.get() * level * gunLevel;
        event.setBaseAmount(event.getBaseAmount() * (1.0F + (float) (percent / 100.0)));
    }

    @SubscribeEvent
    public void onGunHurtEqualizer(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.EQUALIZER_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double chance = Config.ENCH_EQUALIZER_TRIGGER_CHANCE_PERCENT_PER_LEVEL.get() * level / 100.0;
        if (chance >= 1.0 || shooter.getRandom().nextDouble() < chance) {
            if (target.getHealth() > shooter.getHealth()) {
                target.setHealth(shooter.getHealth());
            }
        }
    }

    @SubscribeEvent
    public void onGunHurtRandom(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target)) {
            return;
        }
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.RANDOM_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double effectChance = Config.ENCH_RANDOM_EFFECT_CHANCE_PERCENT_PER_LEVEL.get() * level / 100.0;
        if (effectChance >= 1.0 || shooter.getRandom().nextDouble() < effectChance) {
            if (!RANDOM_EFFECTS.isEmpty()) {
                MobEffect effect = RANDOM_EFFECTS.get(target.getRandom().nextInt(RANDOM_EFFECTS.size()));
                int duration = effect == MobEffects.HARM
                        ? 1
                        : Config.ENCH_RANDOM_EFFECT_DURATION_TICKS.get();
                target.addEffect(new MobEffectInstance(effect, duration, 0));
            }
        }
        // 随机附魔: 伤害倍率在 (1 - 0.2n) ~ (1 + 0.25n) 之间均匀随机, n 为附魔等级; 最低钳制到 0
        float minMult = Math.max(0.0F, 1.0F - 0.2F * level);
        float maxMult = 1.0F + 0.25F * level;
        float mult = minMult + shooter.getRandom().nextFloat() * (maxMult - minMult);
        event.setBaseAmount(event.getBaseAmount() * mult);
    }

    @SubscribeEvent
    public void onGunHurtDecapitation(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.DECAPITATION_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        Entity bullet = event.getBullet();
        if (bullet == null) {
            return;
        }
        float per = (float) (Config.ENCH_DECAPITATION_HEADSHOT_BONUS_PERCENT_PER_LEVEL.get() / 100.0 * level);
        DecapitationHelper.onBulletHitEntity(bullet, shooter, event.isHeadShot(), per);
        float bonus = DecapitationHelper.getBonus(shooter.getUUID());
        if (bonus > 0.0F) {
            event.setBaseAmount(event.getBaseAmount() * (1.0F + bonus));
        }
    }

    private static final List<MobEffect> RANDOM_EFFECTS = List.of(
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.HARM, MobEffects.CONFUSION,
            MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.POISON,
            MobEffects.WITHER, MobEffects.UNLUCK, MobEffects.BAD_OMEN, MobEffects.DARKNESS,
            MobEffects.LEVITATION, MobEffects.GLOWING);

    @SubscribeEvent
    public void onGunHurtCharge(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.CHARGE_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        double speed = getChargeSpeed(shooter);
        if (speed <= 0.0) {
            return;
        }
        double percent = Config.ENCH_CHARGE_DAMAGE_PERCENT_PER_SPEED_PER_LEVEL.get() * level * speed;
        event.setBaseAmount(event.getBaseAmount() * (1.0F + (float) (percent / 100.0)));
    }

    private static final int CHARGE_SPEED_SAMPLE_WINDOW = 5;
    private static final Map<UUID, PlayerSpeedTracker> CHARGE_PLAYER_SPEED = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTickChargeSpeed(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        CHARGE_PLAYER_SPEED.computeIfAbsent(player.getUUID(), k -> new PlayerSpeedTracker())
                .record(player.position(), player.level().getGameTime());
    }

    private double getChargeSpeed(LivingEntity shooter) {
        if (shooter instanceof Player player) {
            PlayerSpeedTracker tracker = CHARGE_PLAYER_SPEED.get(player.getUUID());
            if (tracker != null) {
                double speed = tracker.speed(player.position(), player.level().getGameTime());
                if (speed >= 0.0) {
                    return speed;
                }
            }
        }
        return shooter.getDeltaMovement().length() * 20.0;
    }

    private static class PlayerSpeedTracker {
        private final Vec3[] positions = new Vec3[CHARGE_SPEED_SAMPLE_WINDOW];
        private final long[] ticks = new long[CHARGE_SPEED_SAMPLE_WINDOW];
        private int cursor = 0;

        void record(Vec3 pos, long tick) {
            positions[cursor] = pos;
            ticks[cursor] = tick;
            cursor = (cursor + 1) % CHARGE_SPEED_SAMPLE_WINDOW;
        }

        double speed(Vec3 currentPos, long currentTick) {
            long bestElapsed = -1;
            int bestIdx = -1;
            for (int i = 0; i < CHARGE_SPEED_SAMPLE_WINDOW; i++) {
                if (positions[i] == null) {
                    continue;
                }
                long elapsed = currentTick - ticks[i];
                if (elapsed >= 1 && elapsed <= CHARGE_SPEED_SAMPLE_WINDOW && elapsed > bestElapsed) {
                    bestElapsed = elapsed;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                return -1.0;
            }
            return currentPos.distanceTo(positions[bestIdx]) / (double) bestElapsed * 20.0;
        }
    }

    @SubscribeEvent
    public void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        applyEmberExplosion(event);
        LivingEntity shooter = event.getAttacker();
        if (shooter == null || event.getKilledEntity() == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int channeling = GunEnchantmentHelper.getLevel(gun, Enchantments.CHANNELING);
        int mending = GunEnchantmentHelper.getLevel(gun, Enchantments.MENDING);
        int silkTouch = GunEnchantmentHelper.getLevel(gun, Enchantments.SILK_TOUCH);
        if (channeling <= 0 && mending <= 0 && silkTouch <= 0) {
            return;
        }
        if (channeling > 0 && rollChanneling(event.getKilledEntity())) {
            strikeLightning(shooter, event.getKilledEntity());
        }
        if (mending > 0) {
            refillAmmo(shooter, event, gun);
        }
        if (silkTouch > 0) {
            dropHeadForKill(event.getKilledEntity());
        }
    }

    private static final Map<EntityType<?>, Item> SILK_TOUCH_HEADS = Map.of(
            EntityType.ZOMBIE, Items.ZOMBIE_HEAD,
            EntityType.SKELETON, Items.SKELETON_SKULL,
            EntityType.CREEPER, Items.CREEPER_HEAD,
            EntityType.PIGLIN, Items.PIGLIN_HEAD,
            EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL);

    private void dropHeadForKill(LivingEntity killed) {
        Item head = SILK_TOUCH_HEADS.get(killed.getType());
        if (head == null) {
            return;
        }
        if (!killed.level().isClientSide) {
            killed.spawnAtLocation(new ItemStack(head));
        }
    }

    private void refillAmmo(LivingEntity shooter, EntityKillByGunEvent event, ItemStack gun) {
        if (gun.isEmpty() || !(gun.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        if (!(event.getBullet() instanceof EntityKineticBullet kineticBullet)) {
            return;
        }
        ResourceLocation ammoId = kineticBullet.getAmmoId();
        if (ammoId == null || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
            return;
        }
        int count = Config.ENCH_MENDING_AMMO_PER_KILL.get();
        if (count <= 0) {
            return;
        }
        boolean sameGun = Objects.equals(kineticBullet.getGunId(), gunItem.getGunId(gun));
        if (!sameGun || gunItem.useInventoryAmmo(gun)) {
            giveAmmoItem(shooter, ammoId, count);
            return;
        }
        TimelessAPI.getCommonGunIndex(gunItem.getGunId(gun)).ifPresent(index -> {
            int max = AttachmentDataUtils.getAmmoCountWithAttachment(gun, index.getGunData());
            int current = gunItem.getCurrentAmmoCount(gun);
            int toMag = Math.min(count, Math.max(max - current, 0));
            if (toMag > 0) {
                gunItem.setCurrentAmmoCount(gun, current + toMag);
            }
            int overflow = count - toMag;
            if (overflow > 0) {
                giveAmmoItem(shooter, ammoId, overflow);
            }
        });
    }

    private void giveAmmoItem(LivingEntity shooter, ResourceLocation ammoId, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).setCount(count).build();
        ItemStack remaining = shooter.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .map(handler -> ItemHandlerHelper.insertItemStacked(handler, ammoStack, false))
                .orElse(ammoStack);
        if (!remaining.isEmpty() && !shooter.level().isClientSide) {
            shooter.spawnAtLocation(remaining);
        }
    }

    private boolean rollChanneling(LivingEntity context) {
        double chance = Config.ENCH_CHANNELING_TRIGGER_CHANCE.get();
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return context.getRandom().nextDouble() < chance;
    }

    private void strikeLightning(LivingEntity shooter, LivingEntity target) {
        if (Config.ENCH_CHANNELING_ONLY_THUNDER.get() && !shooter.level().isThundering()) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID key = shooter.getUUID();
        Long last = LAST_LIGHTNING.get(key);
        int cooldown = Config.ENCH_STEAL_LIGHTNING_COOLDOWN_MS.get();
        if (last != null && now - last < cooldown) {
            return;
        }
        LAST_LIGHTNING.put(key, now);
        if (shooter.level() instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                if (shooter instanceof ServerPlayer player) {
                    bolt.setCause(player);
                }
                serverLevel.addFreshEntity(bolt);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        int loyalty = GunEnchantmentHelper.getLevelFromShooter(player, Enchantments.LOYALTY);
        Attribute range = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("gunsmithlib", "aim_lock_range"));
        Attribute angle = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.fromNamespaceAndPath("gunsmithlib", "aim_lock_angle"));
        if (range == null || angle == null) {
            return;
        }
        applyLoyaltyModifier(player, range, LOYALTY_RANGE_UUID, loyalty * Config.ENCH_LOYALTY_RANGE_PER_LEVEL.get());
        applyLoyaltyModifier(player, angle, LOYALTY_ANGLE_UUID, loyalty * Config.ENCH_LOYALTY_ANGLE_PER_LEVEL.get());
    }

    private void applyLoyaltyModifier(Player player, Attribute attribute, UUID uuid, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        if (amount > 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, "taczfixes:loyalty", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    @SubscribeEvent
    public void onGunHurtAbyssGazer(EntityHurtByGunEvent.Post event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        int level = GunEnchantmentHelper.getLevelFromShooter(event.getGunId(), shooter,
                TaczFixesMod.ABYSS_GAZER_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        float maxHealth = target.getMaxHealth();
        float currentHealth = target.getHealth();
        if (maxHealth <= 0.0F) {
            return;
        }
        double chance = (maxHealth - currentHealth) / maxHealth;
        if (chance < 1.0 && shooter.getRandom().nextDouble() >= chance) {
            return;
        }
        playAbyssKillEffect(target);
        instantKill(target, shooter, ABYSS_DAMAGE_ID);
    }

    private static final Map<UUID, Integer> BULLET_PENETRATION = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onGunHurtFocusedAmmo(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.FOCUSED_AMMO_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        Entity bullet = event.getBullet();
        if (bullet == null) {
            return;
        }
        int count = BULLET_PENETRATION.getOrDefault(bullet.getUUID(), 0);
        if (count > 0) {
            double mult = Math.pow(1.0 + Config.ENCH_FOCUSED_AMMO_DAMAGE_MULT_PER_LEVEL.get() * level, count);
            event.setBaseAmount(event.getBaseAmount() * (float) mult);
        }
        BULLET_PENETRATION.put(bullet.getUUID(), count + 1);
    }

    @SubscribeEvent
    public void onGunHurtPatience(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        event.setBaseAmount(com.ssscript.taczfixes.common.util.PatienceHelper.applyToBullet(
                event.getBullet(), event.getBaseAmount()));
    }

    @SubscribeEvent
    public void onPlayerTickPatience(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        com.ssscript.taczfixes.common.util.PatienceHelper.onPlayerTick(event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_LIGHTNING.remove(id);
        CHARGE_PLAYER_SPEED.remove(id);
        CARDIAC_TARGETS.entrySet().removeIf(entry -> entry.getValue().equals(id));
        FROZEN.remove(id);
    }

    @SubscribeEvent
    public void onBulletLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof com.tacz.guns.entity.EntityKineticBullet bullet) {
            BULLET_PENETRATION.remove(bullet.getUUID());
        }
    }

    // ================================================================================
    // 猩红终焉 / 常凪 / 余烬 / 蓄势待发 / 封焰时刻(与旧附魔统一放在本处理器)
    // ================================================================================

    private record Freeze(ResourceKey<Level> dimension, UUID target, double x, double y, double z,
                          long until, boolean hadNoAi, boolean wasNoAi) {
    }

    /** 封焰时刻: 命中伤害随过热进度提升(0~100% 线性 × 等级 × 每级百分比)。 */
    private static void applySealFlareDamage(EntityHurtByGunEvent.Pre event, LivingEntity shooter,
                                             LivingEntity target) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        int level = GunEnchantmentUtil.level(gun, TaczFixesMod.SEAL_FLARE_ENCHANTMENT);
        if (level <= 0) {
            return;
        }
        float heat = GunEnchantmentUtil.heatProgress(gun);
        if (heat <= 0.0F) {
            return;
        }
        float multiplier = 1.0F + heat * level
                * (float) (Config.ENCH_SEAL_FLARE_DAMAGE_PERCENT_PER_HEAT_PER_LEVEL.get() / 100.0D);
        event.setBaseAmount(event.getBaseAmount() * multiplier);
    }

    /** 猩红终焉(标记目标) + 常凪(冻结): 命中后触发。 */
    private static void applyScarletAndTokonagi(EntityHurtByGunEvent.Post event, LivingEntity shooter,
                                               LivingEntity target) {
        if (!GunEnchantmentHelper.isEnabled() || !target.isAlive()) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(event.getGunId(), shooter);
        if (gun.isEmpty()) {
            return;
        }
        if (GunEnchantmentUtil.level(gun, TaczFixesMod.SCARLET_DEATH_ENCHANTMENT) > 0) {
            CARDIAC_TARGETS.put(target.getUUID(), shooter.getUUID());
        }
        int tokonagi = GunEnchantmentUtil.level(gun, TaczFixesMod.TOKONAGI_ENCHANTMENT);
        if (tokonagi > 0) {
            int ticks = (int) (Math.floor(event.getAmount()) * tokonagi
                    * Config.ENCH_TOKONAGI_TICKS_PER_DAMAGE.get());
            applyFreeze(target, ticks);
        }
    }

    /** 余烬: 击杀实体时引发一次不破坏方块的爆炸, 并点燃半径内的实体。 */
    private static void applyEmberExplosion(EntityKillByGunEvent event) {
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        LivingEntity killed = event.getKilledEntity();
        if (shooter == null || killed == null) {
            return;
        }
        int level = GunEnchantmentUtil.level(GunEnchantmentHelper.getGunStack(event.getGunId(), shooter),
                TaczFixesMod.EMBER_ENCHANTMENT);
        if (level <= 0) {
            return;
        }
        if (!(killed.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double x = killed.getX();
        double y = killed.getY();
        double z = killed.getZ();
        // 爆炸威力 = 1 + 附魔等级, 不破坏方块
        serverLevel.explode(null, x, y, z, 1.0F + level, false, Level.ExplosionInteraction.NONE);
        // 点燃半径 0.5 + 1.5×等级 内的实体, 持续 10 + 10×等级 tick
        double radius = 0.5D + 1.5D * level;
        int fireTicks = 10 + level * 10;
        double radiusSqr = radius * radius;
        AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (entity == killed || entity.fireImmune() || entity.distanceToSqr(x, y, z) > radiusSqr) {
                continue;
            }
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireTicks));
        }
    }

    /** 蓄势待发: 持枪蓄力时受到的伤害减少 等级×15%。 */
    @SubscribeEvent
    public void onLivingHurtPoised(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        ItemStack gun = GunEnchantmentHelper.getGunStack(entity);
        if (gun.isEmpty()) {
            return;
        }
        int level = GunEnchantmentUtil.level(gun, TaczFixesMod.POISED_ENCHANTMENT);
        if (level <= 0 || !GunEnchantmentUtil.isCharging(entity)) {
            return;
        }
        float reduction = Math.min(1.0F, level
                * (float) (Config.ENCH_POISED_REDUCTION_PERCENT_PER_LEVEL.get() / 100.0D));
        event.setAmount(event.getAmount() * (1.0F - reduction));
    }

    /** 猩红终焉: 射手死亡时, 其标记过的目标全部因心脏骤停死亡。 */
    @SubscribeEvent
    public void onLivingDeathScarletDeath(LivingDeathEvent event) {
        UUID dead = event.getEntity().getUUID();
        CARDIAC_TARGETS.remove(dead);
        if (event.getEntity().level().isClientSide) {
            return;
        }
        List<UUID> owned = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : CARDIAC_TARGETS.entrySet()) {
            if (entry.getValue().equals(dead)) {
                owned.add(entry.getKey());
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        LivingEntity owner = event.getEntity();
        for (UUID targetId : owned) {
            CARDIAC_TARGETS.remove(targetId);
            Entity entity = findEntity(owner, targetId);
            if (entity instanceof LivingEntity target && target.isAlive()) {
                playScarletKillEffect(target);
                instantKill(target, owner, CARDIAC_DAMAGE_ID);
            }
        }
    }

    /** 猩红终焉标记的红色药水粒子 + 常凪冻结状态维护。 */
    @SubscribeEvent
    public void onServerTickNewEnchants(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        if (!CARDIAC_TARGETS.isEmpty()) {
            tickCardiac(server);
        }
        if (!FROZEN.isEmpty()) {
            tickFreeze(server);
        }
    }

    /**
     * 瞬间击杀: 直接把血量置 0 再走正常死亡流程(绕过伤害减免/图腾), 保留击杀者与自定义死亡提示。
     * 猩红终焉与凝渊者共用。
     */
    public static void instantKill(LivingEntity target, LivingEntity attacker, ResourceLocation damageTypeId) {
        if (target == null || !target.isAlive()) {
            return;
        }
        DamageSource source;
        try {
            Holder<DamageType> holder = target.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId));
            source = new DamageSource(holder, attacker, attacker);
        } catch (Throwable throwable) {
            source = target.damageSources().magic();
        }
        target.setHealth(0.0F);
        if (!target.isRemoved()) {
            // 玩家死亡界面/聊天栏的死亡提示取的是 CombatTracker 里最后一条战斗记录, 而不是 die() 的入参。
            // 直接 setHealth(0)+die() 不经过 hurt(), 战斗记录里最后一条还是之前那发子弹(玩家击杀),
            // 于是玩家死亡时仍然显示"被 xxx 击倒"。这里把自定义伤害源补进战斗记录, 再用它走死亡流程。
            try {
                target.getCombatTracker().recordDamage(source, 0.0F);
            } catch (Throwable ignored) {
                // 记录失败不影响击杀本身。
            }
            ((com.ssscript.taczfixes.common.mixin.LivingEntityDieInvoker) target).taczfixes$die(source);
        }
    }

    private static void applyFreeze(LivingEntity target, int ticks) {
        if (ticks <= 0 || !(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        long until = now + ticks;
        Freeze existing = FROZEN.get(target.getUUID());
        boolean firstFreeze = existing == null;
        if (existing != null) {
            until = Math.max(existing.until(), until);
        }
        boolean mob = target instanceof Mob;
        boolean wasNoAi = mob && ((Mob) target).isNoAi();
        if (mob) {
            ((Mob) target).setNoAi(true);
        }
        FROZEN.put(target.getUUID(), new Freeze(serverLevel.dimension(), target.getUUID(),
                target.getX(), target.getY(), target.getZ(), until, true, wasNoAi));
        syncFreezeEntity(target, ticks);
        if (target instanceof ServerPlayer player) {
            // 客户端需要通过冻结状态来丢弃全部输入。
            syncFreeze(player, ticks);
            showFreezeActionBar(player);
        }
        playTokonagiFreezeEffect(target, firstFreeze);
    }

    // ================================================================================
    // 附魔触发的粒子特效(纯服务端生成, 由原版粒子包同步给附近玩家)
    // ================================================================================

    /** 凝渊者: 紫黑虚空 */
    private static final DustParticleOptions ABYSS_VOID_DUST =
            new DustParticleOptions(new Vector3f(0.46F, 0.12F, 0.92F), 1.5F);
    private static final DustParticleOptions ABYSS_DEEP_DUST =
            new DustParticleOptions(new Vector3f(0.16F, 0.03F, 0.36F), 2.0F);
    /** 常凪: 无间地狱(暗红 + 灵魂火) */
    private static final DustParticleOptions HELL_BLOOD_DUST =
            new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.9F);
    private static final DustParticleOptions HELL_EMBER_DUST =
            new DustParticleOptions(new Vector3f(0.95F, 0.28F, 0.04F), 1.4F);
    /** 猩红终焉: 血红 */
    private static final DustColorTransitionOptions SCARLET_TO_DARK =
            new DustColorTransitionOptions(new Vector3f(0.85F, 0.06F, 0.06F),
                    new Vector3f(0.18F, 0.0F, 0.01F), 1.6F);
    private static final DustParticleOptions SCARLET_DUST =
            new DustParticleOptions(new Vector3f(0.92F, 0.05F, 0.10F), 1.3F);

    /** 凝渊者秒杀: 两层虚空涟漪 + 虚空螺旋内爆 + 幽魂升腾。 */
    public static void playAbyssKillEffect(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double centerY = y + target.getBbHeight() * 0.55D;
        // 中心强闪
        level.sendParticles(ParticleTypes.FLASH, x, centerY, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 地面向外扩散的虚空涟漪(内圈亮紫, 外圈深紫)
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2.0D * i / 28.0D;
            level.sendParticles(ABYSS_VOID_DUST, x + Math.cos(angle) * 1.1D, y + 0.12D,
                    z + Math.sin(angle) * 1.1D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            level.sendParticles(ABYSS_DEEP_DUST, x + Math.cos(angle) * 1.95D, y + 0.1D,
                    z + Math.sin(angle) * 1.95D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 虚空螺旋: 越往上越细, 最终全部汇聚到目标身体中心
        for (int i = 0; i < 44; i++) {
            double t = i / 43.0D;
            double angle = t * Math.PI * 6.0D;
            double radius = 2.8D * (1.0D - t * 0.92D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    x + Math.cos(angle) * radius, centerY + 2.4D * t, z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 向上飘散的幽魂 + 紫色虚空尘埃 + 下坠的传送门粒子
        level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.4D, z, 18, 0.8D, 1.0D, 0.8D, 0.02D);
        level.sendParticles(ABYSS_VOID_DUST, x, centerY, z, 60, 1.4D, 0.9D, 1.4D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL, x, centerY, z, 40, 1.3D, 0.8D, 1.3D, 0.35D);
        level.sendParticles(ParticleTypes.WITCH, x, centerY, z, 14, 0.9D, 0.6D, 0.9D, 0.01D);
    }

    /** 常凪冻结: 无间地狱 —— 地狱火环 + 灵魂火螺旋囚笼 + 余烬/灰烬升腾。refresh 时只补少量火星。 */
    public static void playTokonagiFreezeEffect(LivingEntity target, boolean firstFreeze) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double height = target.getBbHeight();
        double centerY = y + height * 0.5D;
        if (!firstFreeze) {
            // 冻结刷新/延长: 少量火星, 避免连射时刷屏
            level.sendParticles(ParticleTypes.FLAME, x, centerY, z, 8, 0.5D, 0.5D, 0.5D, 0.0D);
            level.sendParticles(HELL_EMBER_DUST, x, centerY, z, 6, 0.6D, 0.5D, 0.6D, 0.0D);
            return;
        }
        // 地面两层地狱火环: 内圈暗红血光, 外圈橙色余烬
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D;
            level.sendParticles(HELL_BLOOD_DUST, x + Math.cos(angle) * 1.6D, y + 0.1D,
                    z + Math.sin(angle) * 1.6D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D + Math.PI / 32.0D;
            level.sendParticles(HELL_EMBER_DUST, x + Math.cos(angle) * 2.4D, y + 0.25D,
                    z + Math.sin(angle) * 2.4D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 火焰螺旋囚笼: 自下而上盘旋并逐渐收紧
        for (int i = 0; i < 44; i++) {
            double t = i / 43.0D;
            double angle = t * Math.PI * 6.0D;
            double radius = 1.7D * (1.0D - t * 0.45D);
            level.sendParticles(ParticleTypes.FLAME,
                    x + Math.cos(angle) * radius, y + 0.15D + height * t, z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 升腾的魂灵、火焰与余烬, 以及闷烧的灰烬黑烟
        level.sendParticles(ParticleTypes.SOUL, x, y + 0.3D, z, 18, 0.7D, 0.9D, 0.7D, 0.015D);
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.2D, z, 30, 0.9D, 0.6D, 0.9D, 0.02D);
        level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + 0.3D, z, 36, 1.2D, 0.9D, 1.2D, 0.02D);
        level.sendParticles(ParticleTypes.ASH, x, centerY, z, 26, 1.1D, 1.0D, 1.1D, 0.01D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2D, z, 20, 1.2D, 0.6D, 1.2D, 0.01D);
        level.sendParticles(ParticleTypes.LAVA, x, centerY, z, 18, 0.9D, 0.8D, 0.9D, 0.6D);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 0.1D, z, 24, 1.0D, 0.7D, 1.0D, 0.02D);
        level.sendParticles(HELL_BLOOD_DUST, x, centerY, z, 50, 1.4D, 1.0D, 1.4D, 0.0D);
    }

    /** 猩红终焉秒杀: 血色冲击环 + 心脏收缩螺旋 + 血雾与伤害标记。 */
    public static void playScarletKillEffect(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double centerY = y + target.getBbHeight() * 0.5D;
        level.sendParticles(ParticleTypes.FLASH, x, centerY, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 血色冲击环
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            level.sendParticles(SCARLET_TO_DARK, x + Math.cos(angle) * 1.9D, y + 0.15D,
                    z + Math.sin(angle) * 1.9D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 逆向收缩的血色螺旋, 像心脏最后一次收束
        for (int i = 0; i < 44; i++) {
            double t = i / 43.0D;
            double angle = -t * Math.PI * 8.0D;
            double radius = 2.4D * (1.0D - t * 0.9D);
            level.sendParticles(SCARLET_DUST,
                    x + Math.cos(angle) * radius,
                    centerY + 1.2D * (1.0D - t) - 0.4D,
                    z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // 心口处的伤害标记、溅射的血雾与猩红孢子
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, centerY, z, 12, 0.5D, 0.4D, 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y + 0.1D, z, 46, 0.9D, 0.7D, 0.9D, 0.02D);
        level.sendParticles(ParticleTypes.CRIT, x, centerY, z, 28, 1.1D, 1.0D, 1.1D, 0.4D);
        level.sendParticles(SCARLET_DUST, x, centerY, z, 60, 1.5D, 1.1D, 1.5D, 0.0D);
    }

    /** 常凪冻结: 在原版 action bar 上显示红色提示。 */
    private static void showFreezeActionBar(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("taczfixes.freeze.text")
                .withStyle(ChatFormatting.RED), true);
    }

    private static void clearFreezeActionBar(ServerPlayer player) {
        player.displayClientMessage(Component.empty(), true);
    }

    /** 把"该实体被冻结"广播给追踪它的玩家(0 = 解除), 用于客户端暂停动画/声音。 */
    private static void syncFreezeEntity(LivingEntity entity, int ticks) {
        ServerMessageTokonagiFreezeEntity message =
                new ServerMessageTokonagiFreezeEntity(entity.getId(), ticks);
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
        // 追踪列表在实体刚被冻结/换维度时可能还没刷新, 附近玩家也补发一次(客户端按更久时间合并, 重复无害)。
        if (entity.level() instanceof ServerLevel serverLevel) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                    entity.getX(), entity.getY(), entity.getZ(), 128.0D, serverLevel.dimension())), message);
        }
    }

    /** 把冻结的剩余 tick 同步给被冻结的玩家(0 = 解除)。 */
    private static void syncFreeze(ServerPlayer player, int ticks) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ServerMessageTokonagiFreeze(ticks));
    }

    public static boolean isFrozen(Entity entity) {
        return entity != null && FROZEN.containsKey(entity.getUUID());
    }

    private static void tickCardiac(MinecraftServer server) {
        long time = server.overworld() == null ? 0L : server.overworld().getGameTime();
        for (Map.Entry<UUID, UUID> entry : CARDIAC_TARGETS.entrySet()) {
            Entity target = findEntity(server, entry.getKey());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                CARDIAC_TARGETS.remove(entry.getKey());
                continue;
            }
            Entity owner = findEntity(server, entry.getValue());
            if (!(owner instanceof LivingEntity)) {
                CARDIAC_TARGETS.remove(entry.getKey());
                continue;
            }
            if (time % 5L != 0L || !(living.level() instanceof ServerLevel serverLevel)) {
                continue;
            }
            double x = living.getX() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
            double y = living.getY() + living.getBbHeight() * (0.35D + living.getRandom().nextDouble() * 0.5D);
            double z = living.getZ() + (living.getRandom().nextDouble() - 0.5D) * living.getBbWidth();
            // ENTITY_EFFECT = 药水粒子; 颜色通过 xDist/yDist/zDist 传入(红色)。
            serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, x, y, z, 0,
                    0.95D, 0.05D, 0.05D, 1.0D);
        }
    }

    private static void tickFreeze(MinecraftServer server) {
        for (Map.Entry<UUID, Freeze> entry : FROZEN.entrySet()) {
            Freeze freeze = entry.getValue();
            ServerLevel level = server.getLevel(freeze.dimension());
            if (level == null) {
                FROZEN.remove(entry.getKey());
                continue;
            }
            Entity entity = level.getEntity(freeze.target());
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                FROZEN.remove(entry.getKey());
                if (entity instanceof LivingEntity removed) {
                    syncFreezeEntity(removed, 0);
                }
                if (entity instanceof ServerPlayer player) {
                    syncFreeze(player, 0);
                    clearFreezeActionBar(player);
                }
                continue;
            }
            if (level.getGameTime() >= freeze.until()) {
                FROZEN.remove(entry.getKey());
                syncFreezeEntity(living, 0);
                if (living instanceof ServerPlayer player) {
                    syncFreeze(player, 0);
                    clearFreezeActionBar(player);
                }
                if (freeze.hadNoAi() && living instanceof Mob mob && !freeze.wasNoAi()) {
                    mob.setNoAi(false);
                }
                continue;
            }
            if (living instanceof ServerPlayer player) {
                // 每 10 tick 刷新一次剩余时长, 防止丢包/重连后客户端还处于冻结或提前解除。
                int remaining = (int) (freeze.until() - level.getGameTime());
                if (remaining % 10 == 0) {
                    syncFreeze(player, remaining);
                }
            }
            if (level.getGameTime() % 10L == 0L) {
                syncFreezeEntity(living, (int) (freeze.until() - level.getGameTime()));
            }
            if (living instanceof ServerPlayer player && level.getGameTime() % 20L == 0L) {
                showFreezeActionBar(player);
            }
            living.setDeltaMovement(0.0D, 0.0D, 0.0D);
            living.fallDistance = 0.0F;
            living.setPos(freeze.x(), freeze.y(), freeze.z());
            if (living instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setNoAi(true);
            }
            if (living instanceof ServerPlayer player) {
                player.connection.teleport(freeze.x(), freeze.y(), freeze.z(), player.getYRot(), player.getXRot());
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static Entity findEntity(LivingEntity context, UUID id) {
        if (context != null && context.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        if (context != null && context.getServer() != null) {
            return findEntity(context.getServer(), id);
        }
        return null;
    }
}
