package com.ssscript.taczfixes.common.util;

import com.ssscript.taczfixes.TaczFixesMod;
import com.tacz.guns.api.item.IGun;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME)
public final class OffhandShieldHandler {
    private static final Map<UUID, Boolean> RAISED = new ConcurrentHashMap<>();

    private OffhandShieldHandler() {
    }

    public static void setRaised(ServerPlayer player, boolean raised) {
        if (!raised || !canRaise(player)) {
            RAISED.remove(player.getUUID());
            if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                player.stopUsingItem();
            }
            return;
        }
        RAISED.put(player.getUUID(), Boolean.TRUE);
        player.startUsingItem(InteractionHand.OFF_HAND);
    }

    public static boolean isRaised(ServerPlayer player) {
        return RAISED.getOrDefault(player.getUUID(), Boolean.FALSE);
    }

    private static boolean canRaise(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        return !offhand.isEmpty()
                && offhand.getItem() instanceof ShieldItem
                && !player.getCooldowns().isOnCooldown(Items.SHIELD)
                && IGun.getIGunOrNull(player.getMainHandItem()) != null
                && IGun.getIGunOrNull(offhand) == null;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RAISED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && tryBlock(player, event.getSource())) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    private static boolean tryBlock(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        if (!isRaised(player)) {
            return false;
        }
        if (!canRaise(player)) {
            RAISED.remove(player.getUUID());
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        ItemStack shield = player.getOffhandItem();
        if (source.getEntity() instanceof LivingEntity attacker
                && attacker.getMainHandItem().canDisableShield(shield, player, attacker)) {
            RAISED.remove(player.getUUID());
            player.stopUsingItem();
            player.getCooldowns().addCooldown(Items.SHIELD, 100);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK,
                    SoundSource.PLAYERS, 0.8F, 0.8F + player.level().random.nextFloat() * 0.4F);
            return true;
        }
        if (player.getCooldowns().isOnCooldown(shield.getItem())) {
            return false;
        }
        Vec3 sourcePosition = source.getSourcePosition();
        if (sourcePosition != null) {
            Vec3 toAttacker = sourcePosition.vectorTo(player.position()).normalize();
            Vec3 view = player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize();
            if (toAttacker.lengthSqr() > 1.0E-6D && toAttacker.dot(view) >= 0.0D) {
                return false;
            }
        }
        shield.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 0.8F + player.level().random.nextFloat() * 0.4F);
        return true;
    }
}
