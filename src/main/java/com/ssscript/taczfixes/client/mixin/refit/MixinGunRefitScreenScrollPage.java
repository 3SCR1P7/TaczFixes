package com.ssscript.taczfixes.client.mixin.refit;

import com.ssscript.taczfixes.client.util.BuiltinAttachmentSlot;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 鼠标悬浮在配件候选列表上时滚轮翻页。 */
@Mixin(ContainerEventHandler.class)
public interface MixinGunRefitScreenScrollPage {

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void taczfixes$scrollPage(double mouseX, double mouseY, double scrollX, double delta,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (delta == 0.0d || !((Object) this instanceof GunRefitScreen screen)) {
            return;
        }
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
        if (!overCandidates) {
            return;
        }
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
