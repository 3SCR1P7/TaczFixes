package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.RefitDiagramPreview;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 悬浮改装候选配件时记录预览槽位, 供显示图表使用"安装此配件后"的数值。 */
@Mixin(value = InventoryAttachmentSlot.class, remap = false)
public abstract class MixinInventoryAttachmentSlotPreview {

    @Inject(method = "m_87963_", at = @At("HEAD"), remap = false)
    private void taczfixes$hoverPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (!self.isHoveredOrFocused()) return;
        ItemStack stack = ((InventoryAttachmentSlot) (Object) this).getItemStack();
        if (stack == null || stack.isEmpty()) return;
        String slotId = CustomSlotGuiState.get();
        if (slotId != null) {
            RefitDiagramPreview.setCustom(slotId, stack, self.getX(), self.getY(), self.getWidth(), self.getHeight());
        } else {
            RefitDiagramPreview.setStandard(RefitTransform.getCurrentTransformType(), stack,
                    self.getX(), self.getY(), self.getWidth(), self.getHeight());
        }
    }
}
