package com.ssscript.taczfixes.client.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** 客户端空仓开火音效反馈。由 common 侧通过 DistExecutor 分发, 避免 common mixin 引用客户端类。 */
public final class ClientDryFireFeedback {

    private ClientDryFireFeedback() {
    }

    public static void play(LivingEntity shooter, ItemStack stack) {
        if (!(shooter instanceof LocalPlayer player)) {
            return;
        }
        TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
            ResourceLocation sound = display.getSounds(SoundManager.DRY_FIRE_SOUND);
            if (sound == null) {
                return;
            }
            SoundPlayManager.stopPlayGunSound();
            SoundPlayManager.playClientSound(player, sound, 1.0f, 1.0f,
                    ((Integer) GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get()).intValue());
        });
    }
}
