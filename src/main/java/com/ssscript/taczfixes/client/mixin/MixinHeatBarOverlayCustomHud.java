package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ApricityUI 自定义 HUD 生效时隐藏 TaCZ 原生过热条, 由页面自行绘制。 */
@Mixin(HeatBarOverlay.class)
public class MixinHeatBarOverlayCustomHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$hideHeatBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomHudManager.isApricityRendering()) {
            ci.cancel();
        }
    }
}
