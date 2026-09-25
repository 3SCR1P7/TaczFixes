package com.ssscript.taczfixes.client.mixin;

import com.ssscript.taczfixes.client.util.CustomSlotGuiState;
import com.ssscript.taczfixes.client.util.PosAlterGuiState;
import com.ssscript.taczfixes.client.util.RefitViewMode;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 改装界面内直接拖动枪械模型: 左键旋转、右键平移、滚轮缩放。
 * 点击/滚轮落在控件(配件槽、按钮、搜索框等)上时放行给界面处理; 挂饰选择面板同理。
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerRefitViewInput {

    private static final String CHARM_BUTTON_CLASS = "com.VvvV.taczcharms.client.gui.CharmPaletteButton";
    private static final String CHARM_PICKER_CLASS = "com.VvvV.taczcharms.client.gui.CharmInventoryPicker";
    private static final String CHARM_ANCHOR_CLASS = "com.VvvV.taczcharms.client.gui.CharmAnchorSelection";
    private static java.lang.reflect.Field taczfixes$pickerExpanded;
    private static java.lang.reflect.Method taczfixes$pickerPanelX;
    private static java.lang.reflect.Method taczfixes$pickerPanelBottom;
    private static java.lang.reflect.Method taczfixes$anchorActive;

    @Inject(method = "m_91530_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$refitModelPress(long windowPointer, int button, int action, int mods, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GunRefitScreen refitScreen)) return;
        if (ci.isCancelled()) return;
        double mx = taczfixes$refitToGuiX(Minecraft.getInstance().mouseHandler.xpos());
        double my = taczfixes$refitToGuiY(Minecraft.getInstance().mouseHandler.ypos());
        if (button == 0) {
            if (action == GLFW.GLFW_PRESS) {
                if (PosAlterGuiState.isHoveringSlider(mx, my) || taczfixes$passToScreen(mx, my)
                        || taczfixes$overWidget(refitScreen, mx, my)) {
                    return;
                }
                RefitViewMode.beginDrag(mx, my);
                ci.cancel();
            } else if (action == GLFW.GLFW_RELEASE) {
                if (RefitViewMode.isDragging()) {
                    RefitViewMode.endDrag();
                    ci.cancel();
                }
            }
        } else if (button == 1) {
            if (action == GLFW.GLFW_PRESS) {
                if (taczfixes$passToScreen(mx, my) || taczfixes$overWidget(refitScreen, mx, my)) {
                    return;
                }
                RefitViewMode.beginPan(mx, my);
                ci.cancel();
            } else if (action == GLFW.GLFW_RELEASE) {
                if (RefitViewMode.isPanning()) {
                    RefitViewMode.endPan();
                    ci.cancel();
                }
            }
        } else if (button == 2) {
            if (action == GLFW.GLFW_PRESS) {
                taczfixes$resetViewModel(refitScreen);
                ci.cancel();
            } else if (action == GLFW.GLFW_RELEASE) {
                ci.cancel();
            }
        }
    }

    /** 中键: 重置缩放/旋转/平移(缓动), 并取消当前配件槽选择。 */
    private static void taczfixes$resetViewModel(GunRefitScreen screen) {
        RefitViewMode.beginReset();
        boolean custom = CustomSlotGuiState.get() != null;
        if (custom) {
            CustomSlotGuiState.beginRefitViewTransition();
            CustomSlotGuiState.reset();
        }
        if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            RefitTransform.changeRefitScreenView(AttachmentType.NONE);
        }
        if (custom) {
            screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        }
    }

    @Inject(method = "m_91526_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$refitModelScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GunRefitScreen refitScreen)) return;
        if (taczfixes$pickerExpanded()) return;
        double mx = taczfixes$refitToGuiX(Minecraft.getInstance().mouseHandler.xpos());
        double my = taczfixes$refitToGuiY(Minecraft.getInstance().mouseHandler.ypos());
        if (PosAlterGuiState.isHoveringSlider(mx, my) || taczfixes$overWidget(refitScreen, mx, my)) {
            return;
        }
        RefitViewMode.addScroll(yOffset);
        ci.cancel();
    }

    @Inject(method = "m_91561_", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczfixes$refitModelMove(long windowPointer, double xpos, double ypos, CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof GunRefitScreen)) return;
        double gx = taczfixes$refitToGuiX(xpos);
        double gy = taczfixes$refitToGuiY(ypos);
        RefitViewMode.updateCursor(gx, gy);
        if (RefitViewMode.isDragging() || RefitViewMode.isPanning()) {
            RefitViewMode.dragTo(gx, gy);
            RefitViewMode.dragToPan(gx, gy);
            ci.cancel();
        }
    }

    private static boolean taczfixes$overWidget(Screen screen, double mx, double my) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                    && mx >= widget.getX() && mx <= widget.getX() + widget.getWidth()
                    && my >= widget.getY() && my <= widget.getY() + widget.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private static boolean taczfixes$passToScreen(double mx, double my) {
        if (taczfixes$hitCharmButton(mx, my)) return true;
        if (taczfixes$pickerExpanded() && taczfixes$inPickerPanel(mx, my)) return true;
        return taczfixes$anchorActive();
    }

    private static boolean taczfixes$hitCharmButton(double mx, double my) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GunRefitScreen)) return false;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.getClass().getName().equals(CHARM_BUTTON_CLASS)
                    && mx >= widget.getX() && mx <= widget.getX() + widget.getWidth()
                    && my >= widget.getY() && my <= widget.getY() + widget.getHeight()) {
                return true;
            }
        }
        return false;
    }

    private static void taczfixes$initCharmReflection() {
        if (taczfixes$pickerExpanded != null) return;
        try {
            Class<?> picker = Class.forName(CHARM_PICKER_CLASS);
            taczfixes$pickerExpanded = picker.getDeclaredField("expanded");
            taczfixes$pickerExpanded.setAccessible(true);
            taczfixes$pickerPanelX = picker.getDeclaredMethod("panelX", int.class);
            taczfixes$pickerPanelX.setAccessible(true);
            taczfixes$pickerPanelBottom = picker.getDeclaredMethod("panelBottom", int.class);
            taczfixes$pickerPanelBottom.setAccessible(true);
            Class<?> anchor = Class.forName(CHARM_ANCHOR_CLASS);
            taczfixes$anchorActive = anchor.getMethod("active");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean taczfixes$pickerExpanded() {
        taczfixes$initCharmReflection();
        try {
            return taczfixes$pickerExpanded != null && taczfixes$pickerExpanded.getBoolean(null);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private static boolean taczfixes$anchorActive() {
        taczfixes$initCharmReflection();
        try {
            return taczfixes$anchorActive != null && (Boolean) taczfixes$anchorActive.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean taczfixes$inPickerPanel(double mx, double my) {
        taczfixes$initCharmReflection();
        try {
            if (taczfixes$pickerPanelX == null) return false;
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof GunRefitScreen refitScreen)) return false;
            int panelX = (int) taczfixes$pickerPanelX.invoke(null, refitScreen.width);
            int panelBottom = (int) taczfixes$pickerPanelBottom.invoke(null, refitScreen.height);
            return mx >= panelX && mx <= panelX + 198 && my >= panelBottom - 158 && my <= panelBottom;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static double taczfixes$refitToGuiX(double rawX) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double taczfixes$refitToGuiY(double rawY) {
        Minecraft minecraft = Minecraft.getInstance();
        return rawY * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }
}
