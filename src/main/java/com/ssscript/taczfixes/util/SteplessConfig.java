package com.ssscript.taczfixes.util;

/**
 * 瞄具配件的无极变倍配置，对应 display 文件中的 "stepless" 字段：
 * <pre>
 * "stepless": {
 *   "enable": true,
 *   "zoom_min": 1.0,
 *   "zoom_max": 6.0,
 *   "speed": 0.05,
 *   "zoom_default": 1.0
 * }
 * </pre>
 * 由 Gson 在解析 AttachmentDisplay 时按字段名反序列化，缺失字段使用默认值。
 */
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

    /** 将倍率限制在 [zoom_min, zoom_max]。 */
    public float clampZoom(float value) {
        return Math.max(zoom_min, Math.min(zoom_max, value));
    }
}
