package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

public final class OffhandItemCombat {

    private static final Map<UUID, Long> LAST_ATTACK = new ConcurrentHashMap<>();

    private OffhandItemCombat() {
    }

    public static void attack(ServerPlayer player, int targetId) {
        if (player == null || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        if (!(player.level().getEntity(targetId) instanceof LivingEntity target)
                || target == player || !target.isAlive()) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        if (IGun.getIGunOrNull(main) == null || IGun.getIGunOrNull(offhand) != null
                || offhand.isEmpty()) {
            return;
        }
        double reach = player.isCreative() ? 5.0d : 3.0d;
        if (player.distanceToSqr(target) > reach * reach || !player.hasLineOfSight(target)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = LAST_ATTACK.get(player.getUUID());
        if (previous != null && now - previous < getAttackCooldownMillis(player, offhand)) {
            return;
        }
        LAST_ATTACK.put(player.getUUID(), now);

        float damage = computeDamage(player, main, offhand, target);
        boolean critical = player.fallDistance > 0.0f && !player.onGround() && !player.onClimbable()
                && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger();
        CriticalHitEvent criticalEvent = ForgeHooks.getCriticalHit(
                player, target, critical, critical ? 1.5f : 1.0f);
        if (criticalEvent != null) {
            damage *= criticalEvent.getDamageModifier();
            critical = true;
        }
        boolean sweeping = !critical && !player.isSprinting()
                && player.getAttackStrengthScale(0.5f) > 0.9f
                && (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SWEEPING_EDGE, offhand) > 0
                        || offhand.getItem() instanceof IMeleeWeapon);
        net.minecraft.world.damagesource.DamageSource source = player.damageSources().playerAttack(player);
        boolean hurt = target.hurt(source, damage);
        player.swing(InteractionHand.OFF_HAND, true);
        player.resetAttackStrengthTicker();
        if (!hurt) {
            return;
        }
        player.setLastHurtMob(target);
        target.knockback(0.4d, player.getX() - target.getX(), player.getZ() - target.getZ());
        net.minecraft.sounds.SoundEvent attackSound = damage >= 4.0f
                ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                attackSound, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (player.level() instanceof ServerLevel serverLevel) {
            if (critical) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        8, 0.2d, 0.2d, 0.2d, 0.1d);
            }
            if (sweeping) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SWEEPING_EDGE, offhand);
                float sweepDamage = 1.0f + (level > 0 ? level / (float) (level + 1) : 0.0f);
                for (LivingEntity nearby : player.level().getEntitiesOfClass(
                        LivingEntity.class, target.getBoundingBox().inflate(1.0d, 0.25d, 1.0d))) {
                    if (nearby == player || nearby == target || !player.canAttack(nearby)) {
                        continue;
                    }
                    if (nearby.hurt(source, sweepDamage)) {
                        nearby.knockback(0.4d, player.getX() - nearby.getX(), player.getZ() - nearby.getZ());
                    }
                }
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        if (offhand.isDamageableItem() && damage > 1.0f) {
            offhand.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(InteractionHand.OFF_HAND));
        }
    }

    public static long getAttackCooldownMillis(Player player, ItemStack offhand) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) {
            return 250L;
        }
        Set<UUID> mainModifiers = new HashSet<>();
        for (AttributeModifier modifier : player.getMainHandItem()
                .getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED)) {
            mainModifiers.add(modifier.getId());
        }
        double addition = 0.0d;
        double multiplyBase = 0.0d;
        double multiplyTotal = 1.0d;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (mainModifiers.contains(modifier.getId())) {
                continue;
            }
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0d + modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : offhand.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_SPEED)) {
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0d + modifier.getAmount();
            }
        }
        double speed = (attribute.getBaseValue() + addition) * (1.0d + multiplyBase) * multiplyTotal;
        if (!Double.isFinite(speed) || speed <= 0.0d) {
            speed = 4.0d;
        }
        return Math.max(50L, Math.round(1000.0d / speed));
    }

    private static float computeDamage(ServerPlayer player, ItemStack main, ItemStack offhand, LivingEntity target) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return 1.0f;
        }
        Set<UUID> mainModifiers = new HashSet<>();
        for (AttributeModifier modifier : main.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            mainModifiers.add(modifier.getId());
        }
        double addition = 0.0d;
        double multiplyBase = 0.0d;
        double multiplyTotal = 1.0d;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (mainModifiers.contains(modifier.getId())) {
                continue;
            }
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0d + modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : offhand.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0d + modifier.getAmount();
            }
        }
        double damage = (attribute.getBaseValue() + addition) * (1.0d + multiplyBase) * multiplyTotal;
        damage += EnchantmentHelper.getDamageBonus(offhand, target.getMobType());
        return (float) Math.max(0.0d, damage);
    }
}
