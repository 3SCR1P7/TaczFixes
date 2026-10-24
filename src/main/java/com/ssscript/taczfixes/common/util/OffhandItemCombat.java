package com.ssscript.taczfixes.common.util;

import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class OffhandItemCombat {

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

        ItemStack originalMain = main.copy();
        ItemStack originalOffhand = offhand.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, originalOffhand);
        player.setItemInHand(InteractionHand.OFF_HAND, originalMain);
        List<AttributeModifier> temporary = new ArrayList<>();
        applyOffhandModifiers(player, originalOffhand, Attributes.ATTACK_DAMAGE, temporary);
        applyOffhandModifiers(player, originalOffhand, Attributes.ATTACK_SPEED, temporary);
        try {
            player.attack(target);
        } finally {
            ItemStack damagedOffhand = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            player.setItemInHand(InteractionHand.OFF_HAND, damagedOffhand);
            player.getInventory().setChanged();
            for (AttributeModifier modifier : temporary) {
                removeFrom(player, Attributes.ATTACK_DAMAGE, modifier.getId());
                removeFrom(player, Attributes.ATTACK_SPEED, modifier.getId());
            }
        }
    }

    private static void applyOffhandModifiers(ServerPlayer player, ItemStack offhand,
                                              Attribute attribute, List<AttributeModifier> temporary) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            present.add(modifier.getId());
        }
        for (AttributeModifier modifier : offhand.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(attribute)) {
            if (present.contains(modifier.getId())) {
                continue;
            }
            AttributeModifier copy = new AttributeModifier(UUID.randomUUID(),
                    "taczfixes_offhand", modifier.getAmount(), modifier.getOperation());
            instance.addTransientModifier(copy);
            temporary.add(copy);
        }
    }

    private static void removeFrom(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
