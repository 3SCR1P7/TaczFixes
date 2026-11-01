package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class GunGlintVertexConsumer implements VertexConsumer {
    private final VertexConsumer base;
    private final VertexConsumer glint;
    private final float scale;
    private final float flow;
    private final float time = (System.currentTimeMillis() % 600000L) / 1000.0F;

    private float x;
    private float y;
    private float z;
    private int color = 0xFFFFFFFF;
    private int overlayUv = OverlayTexture.NO_OVERLAY;
    private int lightUv = LightTexture.FULL_BRIGHT;
    private float normalX;
    private float normalY = 1.0F;
    private float normalZ;

    public GunGlintVertexConsumer(VertexConsumer base, VertexConsumer glint, float scale, float flow) {
        this.base = base;
        this.glint = glint;
        this.scale = scale;
        this.flow = flow;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        base.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.color = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
        base.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        base.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.overlayUv = (v & 0xFFFF) << 16 | u & 0xFFFF;
        base.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.lightUv = (v & 0xFFFF) << 16 | u & 0xFFFF;
        base.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        base.setNormal(x, y, z);
        emitGlint();
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v,
                          int overlay, int light, float normalX, float normalY, float normalZ) {
        base.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
        emitGlint(x, y, z, color, overlay, light, normalX, normalY, normalZ);
    }

    private void emitGlint() {
        emitGlint(this.x, this.y, this.z, this.color, this.overlayUv, this.lightUv,
                this.normalX, this.normalY, this.normalZ);
    }

    private void emitGlint(float x, float y, float z, int color, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
        float u;
        float v;
        float ax = Math.abs(normalX);
        float ay = Math.abs(normalY);
        float az = Math.abs(normalZ);
        if (ax >= ay && ax >= az) {
            u = z;
            v = y;
        } else if (ay >= az) {
            u = x;
            v = z;
        } else {
            u = x;
            v = y;
        }
        u = u * scale + time * flow;
        v = v * scale + time * flow;
        glint.addVertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
    }
}
