package com.ssscript.taczfixes.util;

public class SteplessConfig {
    public boolean enable;
    public float zoom_min;
    public float zoom_max;
    public float speed;
    public float zoom_default;

    public SteplessConfig() {
        this.enable = false;
        this.zoom_min = 1.0f;
        this.zoom_max = 6.0f;
        this.speed = 0.05f;
        this.zoom_default = 1.0f;
    }

    public float clampZoom(float value) {
        return Math.max(zoom_min, Math.min(zoom_max, value));
    }
}
