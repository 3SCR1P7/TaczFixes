package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.client.util.dual.OffhandDisplayManager;
import com.ssscript.taczfixes.common.util.CeaseFireTiming;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class ClientCeaseFireTiming {
    private ClientCeaseFireTiming() {
    }

    public static long delay(LivingEntity shooter, InteractionHand hand, ItemStack stack, IGun gun) {
        if (hand == InteractionHand.OFF_HAND && shooter instanceof LocalPlayer player
                && gun.getFireMode(stack) != FireMode.BURST) {
            long interval = OffhandDisplayManager.getClientState().getShootInterval(player, stack);
            if (interval > 0L) {
                return CeaseFireTiming.afterInterval(interval);
            }
        }
        return CeaseFireTiming.delay(shooter, stack, gun, hand == InteractionHand.MAIN_HAND);
    }
}
