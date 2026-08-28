package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.register.Config;
import com.ssscript.taczfixes.common.util.TaczExplosionContext;
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
