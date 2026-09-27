package com.ssscript.taczfixes.client.render.pbr;

/** Vanilla sky rotation: Y(-90 degrees) * X(timeOfDay * 360 degrees). */
public record CelestialLight(float x, float y, float red, float green, float blue, float ambient) {
    public static CelestialLight sample(float timeOfDay, float rain, float thunder, float moonBrightness) {
        double angle = timeOfDay * Math.PI * 2;
        float sunX = (float) -Math.sin(angle), sunY = (float) Math.cos(angle);
        boolean day = sunY >= 0;
        float elevation = Math.abs(sunY);
        float horizon = Math.min(1f, elevation / 0.12f);
        horizon = horizon * horizon * (3f - 2f * horizon);
        float weather = (1f - rain) * (1f - thunder * 0.75f);
        float strength = horizon * weather * (day ? 1f : 0.16f * moonBrightness);
        // Low sun is warmer; moonlight is dimmer and cool.
        float warmth = Math.min(1f, elevation / 0.35f);
        return new CelestialLight(day ? sunX : -sunX, elevation,
                strength * (day ? 1f : 0.55f),
                strength * (day ? 0.55f + 0.40f * warmth : 0.68f),
                strength * (day ? 0.30f + 0.60f * warmth : 1f),
                (0.06f + 0.94f * Math.max(sunY, 0f)) * (1f - 0.5f * rain));
    }
}
