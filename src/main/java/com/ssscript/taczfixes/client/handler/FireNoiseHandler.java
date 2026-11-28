package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.util.NoiseState;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT)
public final class FireNoiseHandler {
    private FireNoiseHandler() {
    }

    @SubscribeEvent
    public static void onMainHandFire(GunFirePostEvent event) {
        if (event.getLogicalSide().isClient() && event.getShooter() instanceof LocalPlayer player
                && event.getGunItemStack() == player.getMainHandItem()) {
            NoiseState.onShot(player, InteractionHand.MAIN_HAND, event.getGunItemStack());
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        NoiseState.tick();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            NoiseState.reset();
        }
    }
}
