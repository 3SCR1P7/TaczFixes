package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.GunTransparency;
import com.ssscript.taczfixes.common.config.Config;
import com.tacz.guns.client.resource.GunDisplayInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunDisplayInstance.class, remap = false)
public abstract class MixinGunDisplayInstanceTransparency {

    @Inject(method = "enablesTransparency", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$allowTranslucentGun(CallbackInfoReturnable<Boolean> cir) {
        if (Config.GUN_TRANSLUCENT_RENDER.get()
                && GunTransparency.needsTranslucent(((GunDisplayInstance) (Object) this).getModelTexture())) {
            cir.setReturnValue(true);
        }
    }
}
