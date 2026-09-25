package com.ssscript.taczfixes.client.mixin;

import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryAttachmentSlot.class)
public class MixinInventoryAttachmentSlotScale {

    @Unique
    private boolean taczfixes$slotScalePushed;
    @Unique
    private int taczfixes$slotOrigWidth;
    @Unique
    private int taczfixes$slotOrigHeight;

    /** 配件槽尺寸配置: 渲染时临时还原为 18px 原生尺寸, 再以槽位左上角为原点整体缩放, 避免纹理采样越界。 */
    @Inject(method = "m_87963_", at = @At("HEAD"), remap = false)
    private void taczfixes$pushSlotScale(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        taczfixes$slotScalePushed = false;
        net.minecraft.client.gui.components.AbstractWidget widget =
                (net.minecraft.client.gui.components.AbstractWidget) (Object) this;
        float scale = widget.getWidth() / (float) com.tacz.guns.client.gui.GunRefitScreen.SLOT_SIZE;
        if (scale == 1f) return;
        taczfixes$slotOrigWidth = widget.getWidth();
        taczfixes$slotOrigHeight = widget.getHeight();
        widget.setWidth(com.tacz.guns.client.gui.GunRefitScreen.SLOT_SIZE);
        widget.setHeight(com.tacz.guns.client.gui.GunRefitScreen.SLOT_SIZE);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(widget.getX(), widget.getY(), 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-widget.getX(), -widget.getY(), 0);
        taczfixes$slotScalePushed = true;
    }

    @Inject(method = "m_87963_", at = @At("RETURN"), remap = false)
    private void taczfixes$popSlotScale(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        if (!taczfixes$slotScalePushed) return;
        net.minecraft.client.gui.components.AbstractWidget widget =
                (net.minecraft.client.gui.components.AbstractWidget) (Object) this;
        graphics.pose().popPose();
        widget.setWidth(taczfixes$slotOrigWidth);
        widget.setHeight(taczfixes$slotOrigHeight);
        taczfixes$slotScalePushed = false;
    }
}
