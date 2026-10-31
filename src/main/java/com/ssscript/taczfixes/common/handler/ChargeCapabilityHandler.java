package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.common.compat.ChargeCapabilityProvider;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ChargeCapabilityHandler {
    private static final ChargeCapabilityProvider PROVIDER = new ChargeCapabilityProvider();

    @SubscribeEvent
    public void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof IGun) {
                event.registerItem(Capabilities.EnergyStorage.ITEM, PROVIDER, item);
            }
        }
    }
}
