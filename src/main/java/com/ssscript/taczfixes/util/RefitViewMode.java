package com.ssscript.taczfixes.util;

import com.ssscript.taczfixes.Config;

public final class RefitViewMode {
    private static final float SENSITIVITY = 0.4f;
    private static boolean active;
    private static boolean transitioning;
    private static float yawDeg;
    private static float rollDeg;
    private static float distance = 1f;
    private static int buttonX;
    private static int buttonY;
    private static int buttonWidth;
    private static int buttonHeight;
    private static boolean dragging;
    private static double lastMouseX;
    private static double lastMouseY;
    private static double cursorX;
    private static double cursorY;

    private RefitViewMode() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        if (active == value) return;
        active = value;
        dragging = false;
        if (value) {
            transitioning = false;
        } else {
            transitioning = true;
        }
    }

    public static boolean isTransitioning() {
        return transitioning;
    }

    public static void updateTransition() {
        yawDeg = normalizeDeg(yawDeg);
        rollDeg = normalizeDeg(rollDeg);
        yawDeg *= 0.85f;
        rollDeg *= 0.85f;
        distance = 1f + (distance - 1f) * 0.85f;
        if (Math.abs(yawDeg) < 0.01f && Math.abs(rollDeg) < 0.01f
                && Math.abs(distance - 1f) < 0.001f) {
            yawDeg = 0f;
            rollDeg = 0f;
            distance = 1f;
            transitioning = false;
        }
    }

    private static float normalizeDeg(float v) {
        v = v % 360f;
        if (v > 180f) {
            v -= 360f;
        } else if (v < -180f) {
            v += 360f;
        }
        return v;
    }

    public static void toggle() {
        setActive(!active);
    }

    public static void addRotation(float dx, float dy) {
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
        double min = Config.REFIT_VIEW_ZOOM_MIN.get();
        double max = Math.max(min, Config.REFIT_VIEW_ZOOM_MAX.get());
        distance = (float) Math.max(min, Math.min(max,
                distance + scrollY * 0.1f));
    }

    public static float getDistance() {
        return distance;
    }

    public static void setButtonBounds(int x, int y, int width, int height) {
        buttonX = x;
        buttonY = y;
        buttonWidth = width;
        buttonHeight = height;
    }

    public static boolean hitButton(double mouseX, double mouseY) {
        return mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
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
}
