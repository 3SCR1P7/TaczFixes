package com.ssscript.taczfixes.client.mixin.dual;

import com.ssscript.taczfixes.client.client.DualWieldClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftStartUseItem {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void taczfixes$beginVanillaUse(CallbackInfo ci) {
        DualWieldClient.beginVanillaUse();
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void taczfixes$endVanillaUse(CallbackInfo ci) {
        DualWieldClient.endVanillaUse(Minecraft.getInstance().player);
    }
}
