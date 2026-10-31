package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.compat.GunSmithTableEnergyProvider;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class GunSmithTableEnergyHandler {
    private static final GunSmithTableEnergyProvider PROVIDER = new GunSmithTableEnergyProvider();

    @SubscribeEvent
    public void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GunSmithTableBlockEntity.TYPE, PROVIDER);
    }
}
