package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.util.CeaseFireTiming;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ClientCeaseFireTiming {
    private ClientCeaseFireTiming() {
    }

    public static long delay(LivingEntity shooter, InteractionHand hand, ItemStack stack, IGun gun) {
        return CeaseFireTiming.delay(shooter, stack, gun, hand == InteractionHand.MAIN_HAND);
    }

    public static boolean isAutomaticFireHeld(LivingEntity shooter, InteractionHand hand, ItemStack stack, IGun gun) {
        return shooter instanceof LocalPlayer && gun != null
                && gun.getFireMode(stack) == FireMode.AUTO
                && ShootKey.SHOOT_KEY.isDown();
    }
}
