package com.ssscript.taczfixes.client.mixin.refit;

import com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 鼠标悬浮在配件候选列表上时, 滚轮翻页。
 * 直接注入界面的 mouseScrolled(界面打开时必然被调用), 翻页复用已有的 RefitTurnPageButton;
 * 方向按按钮自身的 isUpPage 字段判断(TaCZ 两个翻页按钮的 Y 坐标相同, 不能用位置区分)。
 */
@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class MixinGunRefitScreenScrollPage {

    @Inject(method = "m_6050_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$scrollPage(double mouseX, double mouseY, double delta, CallbackInfoReturnable<Boolean> cir) {
        if (delta == 0.0d) return;
        GunRefitScreen screen = (GunRefitScreen) (Object) this;
        boolean overCandidates = false;
        RefitTurnPageButton up = null;
        RefitTurnPageButton down = null;
        for (GuiEventListener child : screen.children()) {
            if ((child instanceof InventoryAttachmentSlot || child instanceof BuiltinAttachmentSlot)
                    && child.isMouseOver(mouseX, mouseY)) {
                overCandidates = true;
            }
            if (child instanceof RefitTurnPageButton button) {
                if (taczfixes$isUpPage(button)) {
                    up = button;
                } else {
                    down = button;
                }
            }
        }
        if (!overCandidates) return;
        RefitTurnPageButton target = delta > 0.0d ? up : down;
        if (target != null && target.active) {
            target.onPress();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean taczfixes$isUpPage(RefitTurnPageButton button) {
        try {
            java.lang.reflect.Field field = RefitTurnPageButton.class.getDeclaredField("isUpPage");
            field.setAccessible(true);
            return field.getBoolean(button);
        } catch (Throwable ignored) {
            return button.getY() < button.getX();
        }
    }
}
