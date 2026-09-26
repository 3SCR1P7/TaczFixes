package com.ssscript.taczfixes.client.handler;

import com.ssscript.taczfixes.client.render.DynamicCrosshair;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 主手开火时给动态准星增加扩散脉冲(副手开火在 DualWieldClient 中通知)。 */
@OnlyIn(Dist.CLIENT)
public class DynamicCrosshairHandler {
    @SubscribeEvent
    public void onGunShoot(GunShootEvent event) {
        if (!event.getLogicalSide().isClient() || !(event.getShooter() instanceof LocalPlayer player)) {
            return;
        }
        DynamicCrosshair.onShot(player, event.getGunItemStack());
    }
}
