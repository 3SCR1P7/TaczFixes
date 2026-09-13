package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.client.util.StaminaClientState;
import com.ssscript.taczfixes.common.register.AimingStaminaBarMode;
import com.ssscript.taczfixes.common.register.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 右下角耐力条(位于上肢耐力条下方): 128x1, 剩余白色(低于20%渐红), 已消耗部分透明。 */
public class StaminaOverlay implements IGuiOverlay {

    private static final int BAR_WIDTH = 128;
    private static final int BAR_HEIGHT = 1;
    /** 与上肢耐力条保持相同的右边缘。 */
    private static final int MARGIN_X = 10;
    /** 与屏幕底部的距离。 */
    private static final int MARGIN_Y = 7;
    private static final long SMART_HIDE_DELAY_MS = 500L;
    private static final float FADE_PER_SECOND = 2f;
    private static final float SMOOTH_RATE = 14f;
    private static final float RED_THRESHOLD_RATIO = 0.2f;

    private static float alpha = 1f;
    private static float displayStamina = -1f;
    private static long lastFrameMs;
    private static long fullSinceMs = -1L;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        AimingStaminaBarMode mode = Config.STAMINA_BAR_MODE.get();
        if (mode == AimingStaminaBarMode.NEVER) return;
        if (!Config.STAMINA_ENABLED.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) return;
        if (!StaminaClientState.isKnown()) {
            displayStamina = -1f;
            return;
        }
        float max = StaminaClientState.getMax();
        if (max <= 0f) return;
        float stamina = Math.max(0f, StaminaClientState.getStamina());

        long now = System.currentTimeMillis();
        float delta = lastFrameMs == 0L ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        if (displayStamina < 0f) {
            displayStamina = stamina;
        } else {
            float factor = 1f - (float) Math.exp(-delta * SMOOTH_RATE);
            displayStamina += (stamina - displayStamina) * factor;
        }
        float shown = displayStamina;

        float visible = updateAlpha(mode, shown, max, now, delta);
        if (visible <= 0.01f) return;
        int alphaByte = (Math.min(255, Math.round(visible * 255f)) & 0xFF) << 24;

        int x1 = width - MARGIN_X - BAR_WIDTH;
        int y1 = height - MARGIN_Y - BAR_HEIGHT;

        float fraction = Math.max(0f, Math.min(1f, shown / max));
        int fillWidth = Math.round(BAR_WIDTH * fraction);
        if (fillWidth > 0) {
            graphics.fill(x1, y1, x1 + fillWidth, y1 + BAR_HEIGHT, alphaByte | colorFor(shown, max));
        }
    }

    private static float updateAlpha(AimingStaminaBarMode mode, float stamina, float max, long now, float delta) {
        if (mode == AimingStaminaBarMode.ALWAYS) {
            alpha = 1f;
            return alpha;
        }
        boolean full = stamina >= max - 0.001f;
        if (full) {
            if (fullSinceMs < 0L) fullSinceMs = now;
        } else {
            fullSinceMs = -1L;
        }
        float target = full && now - fullSinceMs >= SMART_HIDE_DELAY_MS ? 0f : 1f;
        if (alpha < target) {
            alpha = Math.min(target, alpha + FADE_PER_SECOND * delta);
        } else if (alpha > target) {
            alpha = Math.max(target, alpha - FADE_PER_SECOND * delta);
        }
        return alpha;
    }

    /** 低于上限 20% 时逐渐变红。 */
    private static int colorFor(float stamina, float max) {
        float threshold = max * RED_THRESHOLD_RATIO;
        if (threshold <= 0f) return 0xFFFFFF;
        float t = Math.max(0f, Math.min(1f, (threshold - stamina) / threshold));
        int gb = Math.round(255f * (1f - t));
        return (255 << 16) | (gb << 8) | gb;
    }
}
