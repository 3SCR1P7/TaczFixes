package com.ssscript.taczfixes.client.mixin.compat.parcool;

import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 启用本模组耐力系统时隐藏 ParCool 的耐力条。 */
@Mixin(targets = "com.alrex.parcool.client.hud.stamina.StaminaHUDController", remap = false)
public class MixinParCoolStaminaHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$hideHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Config.STAMINA_ENABLED.get()) {
            ci.cancel();
        }
    }
}
