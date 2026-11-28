package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.ssscript.taczfixes.common.util.SpreadState;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import net.neoforged.bus.api.SubscribeEvent;

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
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (true) {
            SpreadState.tick();
        }
    }
}
