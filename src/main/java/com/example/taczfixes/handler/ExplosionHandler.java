package com.example.taczfixes.handler;

import com.example.taczfixes.Config;
import com.example.taczfixes.util.TaczExplosionContext;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ExplosionHandler {
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!Config.EXPLOSION_BULLET_ONLY.get()) return;
        if (!TaczExplosionContext.isActive()) return;
        event.setCanceled(true);
    }
}
