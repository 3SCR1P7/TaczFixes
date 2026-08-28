package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.register.Config;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinThirdPersonDisabler {
    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void taczfixes$disableThirdPerson(CameraType type, CallbackInfo ci) {
        if (Config.DISABLE_THIRD_PERSON.get() && type != CameraType.FIRST_PERSON) {
            ci.cancel();
        }
    }
}
