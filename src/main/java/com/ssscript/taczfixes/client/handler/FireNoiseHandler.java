package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.util.NoiseState;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT)
public final class FireNoiseHandler {
    private FireNoiseHandler() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFirePostEvent event) {
        if (!event.getLogicalSide().isClient()) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        if (shooter instanceof LocalPlayer player) {
            if (event.getGunItemStack() == player.getMainHandItem()) {
                NoiseState.onShot(player, InteractionHand.MAIN_HAND, event.getGunItemStack());
            }
        } else if (shooter != null) {
            NoiseState.onNearbyShot(shooter, event.getGunItemStack());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NoiseState.tick();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            NoiseState.reset();
        }
    }
}
