package com.ssscript.taczfixes.client.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public final class DualFocusAimKey {
    public static final KeyMapping FOCUS_AIM_KEY = new KeyMapping("key.taczfixes.focus_aim", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, 88, "key.categories.taczfixes");

    private DualFocusAimKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(FOCUS_AIM_KEY);
    }

    public static boolean matches(InputEvent.Key event) {
        return event != null && FOCUS_AIM_KEY.matches(event.getKey(), event.getScanCode());
    }
}
