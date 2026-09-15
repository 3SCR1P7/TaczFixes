package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/client/render/DualFocusAimKey.class */
public final class DualFocusAimKey {
    public static final KeyMapping FOCUS_AIM_KEY = new KeyMapping("key.taczfixes.focus_aim", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, 86, "key.categories.taczfixes");

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
