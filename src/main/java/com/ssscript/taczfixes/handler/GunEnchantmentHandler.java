package com.ssscript.taczfixes.handler;

import com.ssscript.taczfixes.Config;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.util.DecapitationHelper;
import com.ssscript.taczfixes.util.GunEnchantmentHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

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
        int power = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.POWER_ARROWS);
        int smite = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.SMITE);
        int bane = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.BANE_OF_ARTHROPODS);
        int impaling = GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.IMPALING);

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
        if (GunEnchantmentHelper.getLevelFromShooter(shooter, Enchantments.CHANNELING) <= 0) {
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
        int level = GunEnchantmentHelper.getLevelFromShooter(shooter,
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
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
        int level = GunEnchantmentHelper.getLevel(gun, TaczFixesMod.COLLECTOR_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        long otherCount = EnchantmentHelper.getEnchantments(gun).keySet().stream()
                .filter(enchantment -> enchantment != TaczFixesMod.COLLECTOR_ENCHANTMENT.get())
                .count();
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
        int level = GunEnchantmentHelper.getLevelFromShooter(shooter,
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
        int level = GunEnchantmentHelper.getLevelFromShooter(shooter,
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
        int level = GunEnchantmentHelper.getLevelFromShooter(shooter,
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
                TaczFixesMod.PANDORA_PARADOX_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        float current = shooter.getHealth();
        float max = shooter.getMaxHealth();
        if (current <= 0.0F || max <= 0.0F) {
            return;
        }
        event.setBaseAmount(event.getBaseAmount() * (max / current));
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
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
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
        int level = GunEnchantmentHelper.getLevel(GunEnchantmentHelper.getGunStack(shooter),
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
        float mult;
        if (shooter.getRandom().nextFloat() < 0.5F) {
            mult = 0.1F + shooter.getRandom().nextFloat() * 0.9F;
        } else {
            mult = 1.0F + shooter.getRandom().nextFloat() * 9.0F;
        }
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
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
            MobEffects.LEVITATION, MobEffects.GLOWING, MobEffects.INVISIBILITY);

    @SubscribeEvent
    public void onGunHurtCharge(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
    public void onClientTickSmartScope(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!GunEnchantmentHelper.isEnabled()) {
            return;
        }
        LocalPlayer player = mc.player;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || !operator.isAim()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        int level = GunEnchantmentHelper.getLevel(stack, TaczFixesMod.SMART_SCOPE_ENCHANTMENT.get());
        if (level <= 0) {
            return;
        }
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        double distance = Config.ENCH_SMART_SCOPE_MAX_DISTANCE.get();
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(distance));
        BlockHitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double maxDistSqr = blockHit.getType() == HitResult.Type.MISS
                ? distance * distance
                : start.distanceToSqr(blockHit.getLocation());
        if (maxDistSqr <= 0.0) {
            return;
        }
        AABB box = player.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, box,
                e -> e.isAlive() && e.isPickable() && !e.isSpectator());
        double bestDistSqr = maxDistSqr;
        for (Entity e : entities) {
            Optional<Vec3> hit = e.getBoundingBox().clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double d = start.distanceToSqr(hit.get());
            if (d < bestDistSqr) {
                bestDistSqr = d;
            }
        }
        if (bestDistSqr < maxDistSqr) {
            operator.shoot();
        }
    }

    @SubscribeEvent
    public void onGunKill(EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getAttacker();
        if (shooter == null || event.getKilledEntity() == null) {
            return;
        }
        ItemStack gun = GunEnchantmentHelper.getGunStack(shooter);
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
}