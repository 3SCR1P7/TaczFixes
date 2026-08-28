package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.common.util.AmmoReplaceHelper;
import mod.chloeprime.gunsmithlib.api.util.GunInfo;
import mod.chloeprime.gunsmithlib.common.util.GsHelper;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GsHelper.class, remap = false)
public abstract class MixinGsHelper {
    @Inject(method = "getAmmoId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$ammoReplace(GunInfo gunInfo, CallbackInfoReturnable<ResourceLocation> cir) {
        if (gunInfo == null || gunInfo.gunStack() == null || gunInfo.index() == null) {
            return;
        }
        ResourceLocation base = gunInfo.index().getGunData() == null ? null : gunInfo.index().getGunData().getAmmoId();
        ResourceLocation replaced = AmmoReplaceHelper.resolveAmmoId(gunInfo.gunStack(), base);
        if (replaced != null) {
            cir.setReturnValue(replaced);
        }
    }
}