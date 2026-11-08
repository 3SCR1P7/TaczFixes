package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.config.ConfigSync;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Mod-bus listener: schedules one config re-broadcast after any of this mod's common config files reloads. */
@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID)
public final class ConfigSyncReloadHandler {
    private ConfigSyncReloadHandler() {
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.COMMON) {
            return;
        }
        if (!(event.getConfig().getSpec() instanceof ModConfigSpec spec)) {
            return;
        }
        if (!Config.SPECS.containsValue(spec)) {
            return;
        }
        // Client side: a local config reload throws away the in-memory override, re-apply it.
        ConfigSync.reapplyClientOverridesIfNeeded();
        ConfigSyncHandler.markConfigReloaded();
    }
}
