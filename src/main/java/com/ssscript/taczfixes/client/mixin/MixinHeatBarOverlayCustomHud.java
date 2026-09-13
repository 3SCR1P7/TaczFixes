package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.hud.CustomHudDefinition;
import com.ssscript.taczfixes.client.hud.CustomHudManager;
import com.ssscript.taczfixes.client.hud.CustomHudRenderer;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 使用自定义 HUD 时, 过热条仍使用 tacz 原生渲染, 仅按配置调整隐藏/尺寸/位置:
 * 原生以屏幕中心 (width/2, height/2) 为基准绘制, 这里通过位姿变换平移到配置的锚点并缩放。
 */
@Mixin(HeatBarOverlay.class)
public class MixinHeatBarOverlayCustomHud {

    @Unique
    private boolean taczfixes$heatBarShifted = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$shiftHeatBar(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                        int width, int height, CallbackInfo ci) {
        this.taczfixes$heatBarShifted = false;
        CustomHudDefinition def = CustomHudManager.activeHud();
        if (def == null) return;
        CustomHudDefinition.HudElement element = def.overheat_bar;
        if (element == null) return;
        if (element.isHidden()) {
            ci.cancel();
            return;
        }
        float scale = element.size();
        float defaultX = width / 2.0F - 64.0F;
        float defaultY = height / 2.0F - 44.0F;
        float[] pos = "default".equals(element.location())
                ? new float[]{defaultX + element.posX(), defaultY - element.posY()}
                : CustomHudRenderer.anchor(element, 128 * scale, 128 * scale, width, height);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(pos[0], pos[1], 0);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-defaultX, -defaultY, 0);
        this.taczfixes$heatBarShifted = true;
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void taczfixes$restoreHeatBar(ForgeGui gui, GuiGraphics graphics, float partialTick,
                                          int width, int height, CallbackInfo ci) {
        if (this.taczfixes$heatBarShifted) {
            graphics.pose().popPose();
            this.taczfixes$heatBarShifted = false;
        }
    }
}
