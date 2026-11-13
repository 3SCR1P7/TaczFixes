package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.Window;
import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.ssscript.taczfixes.client.client.DualWieldClient;
import com.ssscript.taczfixes.client.client.OffhandDisplayManager;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 浣跨敤鑷畾涔?HUD 鏃堕殣钘?tacz 鑷繁鐨勫噯鏄熺粯鍒?鐢辫嚜瀹氫箟 HUD 鐨?crosshair 鎺ョ)銆? * 涓嶆嫤鎴?onRenderOverlay: tacz 浠嶄細鍙栨秷鍘熺増鍑嗘槦, 淇濊瘉鑷畾涔?HUD 涓嬩笉浼氭樉绀哄師鐗堝噯鏄熴€? */
@Mixin(RenderCrosshairEvent.class)
public class MixinRenderCrosshairEventCustomHud {

    @Shadow(remap = false)
    private static void renderCrosshair(GuiGraphics graphics, Window window) {
        throw new AssertionError();
    }

    /**
     * TACZ only replaces the crosshair when the main hand holds a gun. When the
     * gun is in the offhand, draw the same TACZ crosshair instead of vanilla's.
     */
    @Inject(method = "onRenderCrosshair", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$renderOffhandGunCrosshair(RenderGuiLayerEvent.Pre event, CallbackInfo ci) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || Minecraft.getInstance().screen != null) {
            return;
        }
        if (IGun.getIGunOrNull(player.getMainHandItem()) != null
                || IGun.getIGunOrNull(player.getOffhandItem()) == null) {
            return;
        }
        if (CustomHudManager.isApricityRendering() || OffhandDisplayManager.getClientState().isReloading()) {
            return;
        }
        event.setCanceled(true);
        renderCrosshair(event.getGuiGraphics(), Minecraft.getInstance().getWindow());
        ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczfixes$hideTaczCrosshair(GuiGraphics graphics, Window window, CallbackInfo ci) {
        if (CustomHudManager.isApricityRendering()) {
            ci.cancel();
            return;
        }
        if (com.ssscript.taczfixes.client.client.CrosshairBlockIndicator.render(graphics, window)) {
            ci.cancel();
            return;
        }
        if (com.ssscript.taczfixes.client.client.DynamicCrosshair.render(graphics, window)) {
            ci.cancel();
        }
    }

    /** 鍙屾寔: 涓绘墜鎹㈠脊鏃跺彧鏈夊壇鎵嬩篃鍦ㄦ崲寮规墠闅愯棌鍑嗘槦銆?*/
    @ModifyExpressionValue(method = "onRenderCrosshair", at = @At(value = "INVOKE",
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

    /** 鍙屾寔: 涓绘墜鍔ㄧ敾瑕佹眰闅愯棌鍑嗘槦鏃? 鍚屾牱浠呭壇鎵嬩篃鍦ㄦ崲寮规墠闅愯棌銆?*/
    @ModifyExpressionValue(method = "lambda$onRenderCrosshair$0", at = @At(value = "INVOKE",
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
