package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 自定义槽取消选中时若 refit 视图过渡尚未结束, 延迟到过渡完成再切回 NONE 并重建界面, 避免默认槽被自动选中。 */
@Mixin(value = RefitTransform.class, remap = false)
public class MixinRefitTransformPendingReset {

    @Inject(method = "tickInterpolation", at = @At("RETURN"), remap = false)
    private static void taczfixes$applyPendingCustomSlotDeselect(TickEvent.RenderTickEvent event, CallbackInfo ci) {
        if (!CustomSlotGuiState.hasPendingViewReset()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GunRefitScreen screen)) {
            CustomSlotGuiState.setPendingViewReset(false);
            return;
        }
        if (RefitTransform.getTransformProgress() < 1.0f || RefitTransform.getOpeningProgress() < 1.0f) {
            return;
        }
        if (!RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
            return;
        }
        CustomSlotGuiState.setPendingViewReset(false);
        screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
