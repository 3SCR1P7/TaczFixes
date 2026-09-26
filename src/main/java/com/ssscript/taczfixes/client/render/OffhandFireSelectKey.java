package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public final class OffhandFireSelectKey {
    public static final KeyMapping OFFHAND_FIRE_SELECT_KEY = new KeyMapping("key.taczfixes.offhand_fire_select", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, 89, "key.categories.taczfixes");

    private OffhandFireSelectKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OFFHAND_FIRE_SELECT_KEY);
    }
}
