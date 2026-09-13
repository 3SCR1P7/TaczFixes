package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 使用自定义 HUD 时隐藏 tacz 自己的准星绘制(由自定义 HUD 的 crosshair 接管)。
 * 不拦截 onRenderOverlay: tacz 仍会取消原版准星, 保证自定义 HUD 下不会显示原版准星。
 */
@Mixin(RenderCrosshairEvent.class)
public class MixinRenderCrosshairEventCustomHud {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$hideTaczCrosshair(GuiGraphics graphics, Window window, CallbackInfo ci) {
        if (CustomHudManager.activeHud() != null) {
            ci.cancel();
        }
    }
}
