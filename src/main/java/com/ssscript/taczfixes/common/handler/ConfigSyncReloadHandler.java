package com.ssscript.taczfixes.common.handler;

import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.common.config.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus listener: schedules one config re-broadcast after any of this mod's common config files reloads. */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigSyncReloadHandler {
    private ConfigSyncReloadHandler() {
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.COMMON) {
            return;
        }
        if (!(event.getConfig().getSpec() instanceof ForgeConfigSpec spec)) {
            return;
        }
        if (!Config.SPECS.containsValue(spec)) {
            return;
        }
        // Client side: a local config reload throws away the in-memory override, re-apply it.
        com.ssscript.taczfixes.common.config.ConfigSync.reapplyClientOverridesIfNeeded();
        ConfigSyncHandler.markConfigReloaded();
    }
}
