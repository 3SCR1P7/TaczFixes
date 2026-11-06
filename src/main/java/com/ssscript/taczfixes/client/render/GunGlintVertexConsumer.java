package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class GunGlintVertexConsumer implements VertexConsumer {
    private final VertexConsumer base;
    private final VertexConsumer glint;
    private final float scale;
    private final float flow;
    private final float time = (System.currentTimeMillis() % 600000L) / 1000.0F;

    private float x;
    private float y;
    private float z;
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
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        base.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        base.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        base.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        base.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        base.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.normalX = x;
        this.normalY = y;
        this.normalZ = z;
        base.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        base.endVertex();
        emitGlint();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        base.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        base.unsetDefaultColor();
    }

    private void emitGlint() {
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
        glint.vertex(x, y, z).uv(u, v).endVertex();
    }
}
