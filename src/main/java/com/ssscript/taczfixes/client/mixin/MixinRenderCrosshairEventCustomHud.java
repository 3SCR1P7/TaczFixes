package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.Window;
import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.ssscript.taczfixes.client.render.DualWieldClient;
import com.ssscript.taczfixes.client.render.OffhandDisplayManager;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
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
            return;
        }
        if (com.ssscript.taczfixes.client.render.CrosshairBlockIndicator.render(graphics, window)) {
            ci.cancel();
            return;
        }
        if (com.ssscript.taczfixes.client.render.DynamicCrosshair.render(graphics, window)) {
            ci.cancel();
        }
    }

    /** 双持: 主手换弹时只有副手也在换弹才隐藏准星。 */
    @ModifyExpressionValue(method = "onRenderOverlay", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/entity/ReloadState$StateType;isReloading()Z"), remap = false)
    private static boolean taczfixes$dualReloadCrosshair(boolean reloading) {
        if (!reloading) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DualWieldClient.isDualMode(player)) {
            return true;
        }
        return OffhandDisplayManager.getClientState().isReloading();
    }

    /** 双持: 主手动画要求隐藏准星时, 同样仅副手也在换弹才隐藏。 */
    @ModifyExpressionValue(method = "lambda$onRenderOverlay$0", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/client/animation/statemachine/AnimationStateContext;shouldHideCrossHair()Z"), remap = false)
    private static boolean taczfixes$dualAnimationCrosshair(boolean hide) {
        if (!hide) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DualWieldClient.isDualMode(player)) {
            return true;
        }
        return OffhandDisplayManager.getClientState().isReloading();
    }
}
