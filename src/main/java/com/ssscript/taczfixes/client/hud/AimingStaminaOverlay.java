package com.ssscript.taczfixes.client.hud;

import com.ssscript.taczfixes.client.util.AimingStaminaClientState;
import com.ssscript.taczfixes.common.register.AimingStaminaBarMode;
import com.ssscript.taczfixes.common.register.Config;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 右下角上肢耐力条(位于耐力条上方): 128x1, 剩余白色(低于阈值渐红), 已消耗部分透明。 */
public class AimingStaminaOverlay implements IGuiOverlay {

    private static final int BAR_WIDTH = 128;
    private static final int BAR_HEIGHT = 1;
    private static final int MARGIN = 10;
    /** 与下方耐力条的间距。 */
    private static final int BAR_GAP = 3;
    /** 耐力降到该值时填充色完全变红。 */
    private static final float FULL_RED_AT = 10f;
    private static final long SMART_HIDE_DELAY_MS = 500L;
    private static final float FADE_PER_SECOND = 2f;
    private static final float SMOOTH_RATE = 14f;

    private static float alpha = 1f;
    private static float displayStamina = -1f;
    private static long lastFrameMs;
    private static long fullSinceMs = -1L;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        AimingStaminaBarMode mode = Config.AIMING_STAMINA_BAR_MODE.get();
        if (mode == AimingStaminaBarMode.NEVER) return;
        if (!Config.AIMING_STAMINA_ENABLED.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) return;
        if (!taczfixes$holdsStaminaWeapon(player)) return;
        if (!AimingStaminaClientState.isKnown()) {
            displayStamina = -1f;
            return;
        }
        float max = AimingStaminaClientState.getMax();
        if (max <= 0f) return;
        float stamina = Math.max(0f, AimingStaminaClientState.getStamina());

        long now = System.currentTimeMillis();
        float delta = lastFrameMs == 0L ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        // 服务端每 tick 同步一次, 这里按帧插值让条子平滑移动
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

        int x1 = width - MARGIN - BAR_WIDTH;
        int y1 = height - MARGIN - 1 - BAR_GAP - BAR_HEIGHT;
        int y2 = y1 + BAR_HEIGHT;

        float fraction = Math.max(0f, Math.min(1f, shown / max));
        int fillWidth = Math.round(BAR_WIDTH * fraction);
        if (fillWidth > 0) {
            graphics.fill(x1, y1, x1 + fillWidth, y2, alphaByte | colorFor(shown));
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

    /** 手持 tacz 枪械或 lrtactical 近战武器时显示耐力条。 */
    private static boolean taczfixes$holdsStaminaWeapon(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (IGun.getIGunOrNull(stack) != null) return true;
        if (!net.minecraftforge.fml.ModList.get().isLoaded("lrtactical")) return false;
        try {
            return me.xjqsh.lrtactical.api.item.IMeleeWeapon.of(stack) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 从无法开镜阈值(白色)线性渐变到 10(完全红色)。 */
    private static int colorFor(float stamina) {
        float threshold = Config.AIMING_STAMINA_MIN_TO_AIM.get().floatValue();
        if (threshold <= 0f) return 0xFFFFFF;
        float span = Math.max(0.0001f, threshold - FULL_RED_AT);
        float t = Math.max(0f, Math.min(1f, (threshold - stamina) / span));
        int gb = Math.round(255f * (1f - t));
        return (255 << 16) | (gb << 8) | gb;
    }
}
