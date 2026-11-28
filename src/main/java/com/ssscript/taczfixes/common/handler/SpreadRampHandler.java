package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.SpreadState;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpreadRampHandler {
    @SubscribeEvent
    public void onFire(GunFirePostEvent event) {
        if (event.getLogicalSide().isClient() || event.getShooter() == null) {
            return;
        }
        var stack = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null) {
            var id = TaczFixesDataManager.resolveDataId(gun.getGunId(stack));
            if (id != null && TaczFixesDataManager.usesConfiguredSpreadRecovery(id)) {
                SpreadState.recordLatestFire(event.getShooter(), id);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpreadState.tick();
        }
    }
}
