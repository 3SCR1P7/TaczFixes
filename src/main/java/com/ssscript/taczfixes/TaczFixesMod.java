package com.ssscript.taczfixes;

import com.ssscript.taczfixes.compat.ParCoolSprintInterruptHandler;
import com.ssscript.taczfixes.data.TaczFixesDataHandler;
import com.ssscript.taczfixes.handler.GunAnvilHandler;
import com.ssscript.taczfixes.handler.GunEnchantmentHandler;
import com.ssscript.taczfixes.handler.GunLevelHandler;
import com.ssscript.taczfixes.handler.LimbDamageHandler;
import com.ssscript.taczfixes.handler.SpreadRampHandler;
import com.ssscript.taczfixes.handler.SteplessZoomHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(TaczFixesMod.MOD_ID)
public class TaczFixesMod {
    public static final String MOD_ID = "taczfixes";

    public TaczFixesMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(new LimbDamageHandler());
        MinecraftForge.EVENT_BUS.register(new SpreadRampHandler());
        MinecraftForge.EVENT_BUS.register(new TaczFixesDataHandler());
        MinecraftForge.EVENT_BUS.register(new GunLevelHandler());
        MinecraftForge.EVENT_BUS.register(new GunEnchantmentHandler());
        MinecraftForge.EVENT_BUS.register(new GunAnvilHandler());
        DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    MinecraftForge.EVENT_BUS.register(new SteplessZoomHandler());
                    MinecraftForge.EVENT_BUS.register(new ParCoolSprintInterruptHandler());
                });
    }
}
