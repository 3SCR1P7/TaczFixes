package com.ssscript.taczfixes.common.mixin;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.ssscript.taczfixes.common.util.DualReloadTimeController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ShooterDataHolder.class}, remap = false)
/* loaded from: jar-in-6019096625046612463.jar:com/ssscript/taczfixes/common/mixin/MixinShooterDataHolder.class */
public abstract class MixinShooterDataHolder {
    @Inject(method = {"initialData"}, at = {@At("HEAD")})
    private void dualWield$clearReloadTimeSession(CallbackInfo callback) {
        DualReloadTimeController.end((ShooterDataHolder) (Object) this, null);
    }
}
