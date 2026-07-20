package com.example.taczfixes;

import com.example.taczfixes.handler.LimbDamageHandler;
import com.example.taczfixes.handler.SpreadRampHandler;
import net.minecraftforge.common.MinecraftForge;
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
    }
}
