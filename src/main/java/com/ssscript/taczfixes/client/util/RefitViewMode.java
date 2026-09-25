package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.common.config.Config;
import net.minecraft.util.Mth;

/** 改装界面内直接拖动枪械模型的姿态状态: 左键旋转, 右键平移, 滚轮缩放。 */
public final class RefitViewMode {
    private static final float SENSITIVITY = 0.4f;
    private static final float PAN_SENSITIVITY = 0.00165f;
    private static final float RESET_DURATION = 0.3f;
    private static final float EXIT_RESET_DURATION = 0.22f;
    private static final float ZOOM_SMOOTH_TAU = 0.07f;
    private static float yawDeg;
    private static float rollDeg;
    private static float distance = 1f;
    private static float targetDistance = 1f;
    private static float panX;
    private static float panY;
    private static boolean panning;
    private static boolean dragging;
    private static boolean resetting;
    private static float resetElapsed;
    private static float resetDuration = RESET_DURATION;
    private static float resetStartYaw;
    private static float resetStartRoll;
    private static float resetStartDistance = 1f;
    private static float resetStartPanX;
    private static float resetStartPanY;
    private static long lastTransitionNanos;
    private static double lastMouseX;
    private static double lastMouseY;
    private static double cursorX;
    private static double cursorY;

    private RefitViewMode() {
    }

    public static void reset() {
        yawDeg = 0f;
        rollDeg = 0f;
        distance = 1f;
        targetDistance = 1f;
        panX = 0f;
        panY = 0f;
        panning = false;
        dragging = false;
        resetting = false;
        lastTransitionNanos = 0L;
    }

    /** 中键重置: 缓动回到默认姿态。 */
    public static void beginReset() {
        beginReset(RESET_DURATION);
    }

    /** 退出界面重置: 与收枪动画同步的快速缓动。 */
    public static void beginResetQuick() {
        beginReset(EXIT_RESET_DURATION);
    }

    private static void beginReset(float duration) {
        // 复位走最短路径: 累计角度归一化到 (-180, 180], 避免反向转多圈
        yawDeg = Mth.wrapDegrees(yawDeg);
        rollDeg = Mth.wrapDegrees(rollDeg);
        resetStartYaw = yawDeg;
        resetStartRoll = rollDeg;
        resetStartDistance = distance;
        resetStartPanX = panX;
        resetStartPanY = panY;
        targetDistance = 1f;
        resetting = true;
        dragging = false;
        panning = false;
        resetDuration = duration;
        resetElapsed = 0f;
        lastTransitionNanos = 0L;
    }

    public static void updateTransition() {
        long now = System.nanoTime();
        float dt;
        if (lastTransitionNanos == 0L) {
            dt = 1f / 60f;
        } else {
            dt = Math.min((now - lastTransitionNanos) / 1_000_000_000f, 0.1f);
        }
        lastTransitionNanos = now;
        if (resetting) {
            resetElapsed += dt;
            float t = Math.min(resetElapsed / resetDuration, 1f);
            // easeOutCubic
            float remain = 1f - t;
            float factor = remain * remain * remain;
            yawDeg = resetStartYaw * factor;
            rollDeg = resetStartRoll * factor;
            distance = 1f + (resetStartDistance - 1f) * factor;
            panX = resetStartPanX * factor;
            panY = resetStartPanY * factor;
            if (t >= 1f) {
                yawDeg = 0f;
                rollDeg = 0f;
                distance = 1f;
                targetDistance = 1f;
                panX = 0f;
                panY = 0f;
                resetting = false;
                lastTransitionNanos = 0L;
            }
            return;
        }
        if (Math.abs(targetDistance - distance) > 0.0005f) {
            // 滚轮缩放: 时间常数指数平滑, 与帧率无关
            float factor = (float) Math.exp(-dt / ZOOM_SMOOTH_TAU);
            distance = targetDistance + (distance - targetDistance) * factor;
            if (Math.abs(targetDistance - distance) < 0.0005f) {
                distance = targetDistance;
            }
        }
    }

    public static boolean isIdentity() {
        return yawDeg == 0f && rollDeg == 0f && distance == 1f && panX == 0f && panY == 0f;
    }

    public static void addRotation(float dx, float dy) {
        resetting = false;
        yawDeg += dx * SENSITIVITY;
        rollDeg += dy * SENSITIVITY;
    }

    public static float getYawDeg() {
        return yawDeg;
    }

    public static float getRollDeg() {
        return rollDeg;
    }

    public static void addScroll(double scrollY) {
        resetting = false;
        double min = Config.REFIT_VIEW_ZOOM_MIN.get();
        double max = Math.max(min, Config.REFIT_VIEW_ZOOM_MAX.get());
        targetDistance = (float) Math.max(min, Math.min(max, targetDistance + scrollY * 0.1f));
    }

    public static float getDistance() {
        return distance;
    }

    public static boolean isDragging() {
        return dragging;
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

    public static void beginDrag(double mouseX, double mouseY) {
        resetting = false;
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static void dragTo(double mouseX, double mouseY) {
        if (!dragging) return;
        addRotation((float) (mouseX - lastMouseX), (float) (mouseY - lastMouseY));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static void endDrag() {
        dragging = false;
    }

    public static boolean isPanning() {
        return panning;
    }

    public static void beginPan(double mouseX, double mouseY) {
        resetting = false;
        panning = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static void dragToPan(double mouseX, double mouseY) {
        if (!panning) return;
        panX += (float) (mouseX - lastMouseX) * PAN_SENSITIVITY;
        panY += (float) (mouseY - lastMouseY) * PAN_SENSITIVITY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public static void endPan() {
        panning = false;
    }

    public static float getPanX() {
        return panX;
    }

    public static float getPanY() {
        return panY;
    }
}
