package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public final class OffhandFireSelectKey {
    public static final KeyMapping OFFHAND_FIRE_SELECT_KEY = new KeyMapping("key.taczfixes.offhand_fire_select", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, 89, "key.categories.taczfixes");

    private OffhandFireSelectKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OFFHAND_FIRE_SELECT_KEY);
    }
}
