package com.ssscript.taczfixes.client.render.light;

import org.joml.Vector3f;

import java.nio.FloatBuffer;

/**
 * 单个彩色点光源。移植自 Shimmer (MIT, https://github.com/Low-Drag-MC/Shimmer)。
 * 位置/颜色/半径/强度直接由着色器 UBO 读取。
 */
public class ColorPointLight {
    public static final int STRUCT_SIZE = (4 + 3 + 1);
    public float r, g, b, a;
    public float x, y, z;
    public float radius;
    LightManager lightManager;
    int offset;
    public boolean enable = true;
    final boolean uv;

    protected ColorPointLight(LightManager lightManager, Vector3f pos, int color, float radius, int offset, boolean uv) {
        x = pos.x();
        y = pos.y();
        z = pos.z();
        setColor(color);
        this.lightManager = lightManager;
        this.radius = radius;
        this.offset = offset;
        this.uv = uv;
    }

    public void setColor(int color) {
        a = (((color >> 24) & 0xff) / 255f);
        r = (((color >> 16) & 0xff) / 255f);
        g = (((color >> 8) & 0xff) / 255f);
        b = (((color) & 0xff) / 255f);
    }

    public void setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public boolean isRemoved() {
        return lightManager == null;
    }

    public void remove() {
        if (lightManager != null) {
            lightManager.removeLight(this);
            lightManager = null;
        }
    }

    public void update() {
        if (lightManager != null && offset >= 0 && uv) {
            lightManager.bufferSubData(offset, getData());
        }
    }

    protected float[] getData() {
        return new float[]{r, g, b, a, x, y, z, radius};
    }

    public void uploadBuffer(FloatBuffer buffer) {
        buffer.put(getData());
    }
}
