package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.ssscript.taczfixes.client.util.NoiseState;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class MixinChannelNoise {
    @Shadow @Final private int source;

    @Unique private int taczfixes$noiseFilter;
    @Unique private float taczfixes$appliedNoise;
    @Unique private boolean taczfixes$filterUnavailable;

    @Inject(method = {"play", "updateStream"}, at = @At("HEAD"))
    private void taczfixes$updateNoiseFilter(CallbackInfo ci) {
        float intensity = NoiseState.filterIntensity();
        if (intensity == taczfixes$appliedNoise || taczfixes$filterUnavailable) {
            return;
        }
        if (intensity <= 0.0F) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        } else {
            if (taczfixes$noiseFilter == 0) {
                if (!AL.getCapabilities().ALC_EXT_EFX) {
                    taczfixes$filterUnavailable = true;
                    return;
                }
                taczfixes$noiseFilter = EXTEfx.alGenFilters();
                if (taczfixes$noiseFilter == 0) {
                    taczfixes$filterUnavailable = true;
                    return;
                }
                EXTEfx.alFilteri(taczfixes$noiseFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_BANDPASS);
                if (EXTEfx.alGetFilteri(taczfixes$noiseFilter, EXTEfx.AL_FILTER_TYPE) != EXTEfx.AL_FILTER_BANDPASS) {
                    AL10.alGetError();
                    EXTEfx.alDeleteFilters(taczfixes$noiseFilter);
                    taczfixes$noiseFilter = 0;
                    taczfixes$filterUnavailable = true;
                    return;
                }
            }
            EXTEfx.alFilterf(taczfixes$noiseFilter, EXTEfx.AL_BANDPASS_GAIN, 1.0F - 0.25F * intensity);
            EXTEfx.alFilterf(taczfixes$noiseFilter, EXTEfx.AL_BANDPASS_GAINLF, 1.0F - 0.70F * intensity);
            EXTEfx.alFilterf(taczfixes$noiseFilter, EXTEfx.AL_BANDPASS_GAINHF, 1.0F - 0.92F * intensity);
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, taczfixes$noiseFilter);
        }
        taczfixes$appliedNoise = intensity;
    }

    @Inject(method = "destroy", at = @At("TAIL"))
    private void taczfixes$releaseNoiseFilter(CallbackInfo ci) {
        if (taczfixes$noiseFilter != 0) {
            EXTEfx.alDeleteFilters(taczfixes$noiseFilter);
            taczfixes$noiseFilter = 0;
        }
    }
}
