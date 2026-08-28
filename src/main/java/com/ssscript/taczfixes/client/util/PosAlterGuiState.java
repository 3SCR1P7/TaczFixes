package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.data.GunPosAlterManager;
import com.ssscript.taczfixes.common.network.ClientMessageGunPosAlter;
import com.ssscript.taczfixes.common.network.NetworkHandler;
import com.ssscript.taczfixes.common.util.PosAlterStorage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;

public final class PosAlterGuiState {
    private static final long SEND_INTERVAL_MS = 100L;
    private static final int SLIDER_WIDTH = 120;
    private static final int SLIDER_HEIGHT = 16;
    private static AbstractSliderButton slider;
    private static String slotKey;
    private static String gunId;
    private static boolean dragging;
    private static double cursorX;
    private static double cursorY;
    private static boolean dirty;
    private static long lastSendMs;
    private static int sliderX = Integer.MIN_VALUE;
    private static int sliderY = Integer.MIN_VALUE;

    private PosAlterGuiState() {
    }

    public static void updateCursor(double mouseX, double mouseY) {
        cursorX = mouseX;
        cursorY = mouseY;
    }

    public static double getCursorX() {
        return cursorX;
    }

    public static double getCursorY() {
        return cursorY;
    }

    public static void tickAndRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                     float partialTick) {
        if (RefitViewMode.isActive()) return;
        String key = currentSlotKey();
        String id = currentGunId();
        int x = computeSliderX(screen);
        int y = computeSliderY(screen);
        if (!Objects.equals(key, slotKey) || !Objects.equals(id, gunId)
                || x != sliderX || y != sliderY) {
            flushSend();
            slotKey = key;
            gunId = id;
            sliderX = x;
            sliderY = y;
            slider = (key == null || id == null) ? null : createSlider(screen, key, x, y);
            dragging = false;
            dirty = false;
            lastSendMs = 0L;
        }
        if (dirty && System.currentTimeMillis() - lastSendMs >= SEND_INTERVAL_MS) {
            flushSend();
        }
        if (slider != null) {
            slider.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RefitViewMode.isActive()) return false;
        if (slider == null) return false;
        if (slider.mouseClicked(mouseX, mouseY, button)) {
            dragging = true;
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY) {
        if (!dragging || !(slider instanceof PosAlterSlider posAlterSlider)) return false;
        posAlterSlider.dragToX(mouseX);
        return true;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) return false;
        flushSend();
        dragging = false;
        if (slider != null) {
            slider.mouseReleased(mouseX, mouseY, button);
        }
        return true;
    }

    public static void reset() {
        flushSend();
        slider = null;
        slotKey = null;
        gunId = null;
        dragging = false;
        dirty = false;
        lastSendMs = 0L;
        sliderX = Integer.MIN_VALUE;
        sliderY = Integer.MIN_VALUE;
    }

    private static void markDirty() {
        dirty = true;
    }

    private static void flushSend() {
        if (!dirty) return;
        dirty = false;
        lastSendMs = System.currentTimeMillis();
        if (slider instanceof PosAlterSlider posAlterSlider) {
            NetworkHandler.CHANNEL.sendToServer(
                    new ClientMessageGunPosAlter(posAlterSlider.slotKey, posAlterSlider.currentValue()));
        }
    }

    private static int computeSliderX(Screen screen) {
        return screen.width - 140;
    }

    private static int computeSliderY(Screen screen) {
        if (hasLaserColorSliders(screen)) {
            return screen.height - 84;
        }
        return screen.height - 48;
    }

    private static boolean hasLaserColorSliders(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof HSVSliderGroup.LaserColorSlider) {
                return true;
            }
        }
        return false;
    }

    private static String currentGunId() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return null;
        return igun.getGunId(gunStack).toString();
    }

    private static String currentSlotKey() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return null;
        ResourceLocation gunIdLocal = igun.getGunId(gunStack);
        String customSlot = CustomSlotGuiState.get();
        if (customSlot != null) {
            return GunPosAlterManager.getRange(gunIdLocal, customSlot) != null ? customSlot : null;
        }
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type != null && type != AttachmentType.NONE) {
            String key = type.name().toLowerCase(Locale.US);
            return GunPosAlterManager.getRange(gunIdLocal, key) != null ? key : null;
        }
        return null;
    }

    private static AbstractSliderButton createSlider(Screen screen, String key, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack gunStack = player.getMainHandItem();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) return null;
        float[] range = GunPosAlterManager.getRange(igun.getGunId(gunStack), key);
        if (range == null) return null;
        return new PosAlterSlider(x, y, SLIDER_WIDTH, SLIDER_HEIGHT,
                gunStack, key, range[0], range[1]);
    }

    private static class PosAlterSlider extends AbstractSliderButton {
        private static final ResourceLocation SLIDER_LOCATION = new ResourceLocation("textures/gui/slider.png");
        private final ItemStack gunStack;
        private final String slotKey;
        private final float min;
        private final float max;

        PosAlterSlider(int x, int y, int width, int height, ItemStack gunStack,
                       String slotKey, float min, float max) {
            super(x, y, width, height, Component.translatable("gui.taczfixes.refit_pos_alter.slider", ""),
                    Mth.clamp((PosAlterStorage.get(gunStack, slotKey) - min) / (max - min), 0.0D, 1.0D));
            this.gunStack = gunStack;
            this.slotKey = slotKey;
            this.min = min;
            this.max = max;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double value = this.min + this.value * (this.max - this.min);
            this.setMessage(Component.translatable("gui.taczfixes.refit_pos_alter.slider",
                    String.format(Locale.US, "%.2f", value)));
        }

        @Override
        protected void applyValue() {
            float clamped = currentValue();
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
                ItemStack current = player.getMainHandItem();
                if (IGun.getIGunOrNull(current) != null) {
                    PosAlterStorage.set(current, this.slotKey, clamped);
                }
            }
            markDirty();
        }

        float currentValue() {
            return (float) (this.min + this.value * (this.max - this.min));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            graphics.blitNineSliced(SLIDER_LOCATION, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                    20, 4, 200, 20, 0, (this.isFocused() && !PosAlterGuiState.dragging) ? 20 : 0);
            graphics.blitNineSliced(SLIDER_LOCATION,
                    this.getX() + (int) (this.value * (double) (this.width - 8)), this.getY(),
                    8, this.getHeight(), 20, 4, 200, 20, 0, (this.isHovered ? 60 : 40));
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            int i = this.active ? 16777215 : 10526880;
            this.renderScrollingString(graphics, minecraft.font, 2, i | Mth.ceil(this.alpha * 255.0F) << 24);
        }

        void dragToX(double mouseX) {
            double min = this.getX();
            double max = min + this.getWidth();
            double newValue = Mth.clamp((mouseX - min) / (max - min), 0.0D, 1.0D);
            if (Math.abs(newValue - this.value) < 1e-6) return;
            this.value = newValue;
            this.updateMessage();
            this.applyValue();
        }
    }
}