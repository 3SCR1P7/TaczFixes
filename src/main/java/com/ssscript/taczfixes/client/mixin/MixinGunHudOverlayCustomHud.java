package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ApricityUI 自定义 HUD 生效时隐藏 TaCZ 原生枪械 HUD。 */
@Mixin(GunHudOverlay.class)
public class MixinGunHudOverlayCustomHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$hideTaczHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) return;
        if (CustomHudManager.isApricityRendering()) {
            ci.cancel();
        }
    }
}
