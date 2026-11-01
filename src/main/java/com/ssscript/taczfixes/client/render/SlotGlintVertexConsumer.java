package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class SlotGlintVertexConsumer implements VertexConsumer {
    private static final float SCALE = 0.5F;
    private static final float COS = (float) Math.cos(Math.PI / 18.0D);
    private static final float SIN = (float) Math.sin(Math.PI / 18.0D);

    private final VertexConsumer delegate;
    private final float offsetU;
    private final float offsetV;

    public SlotGlintVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
        long i = (long) (Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0D);
        this.offsetU = -((float) (i % 110000L) / 110000.0F);
        this.offsetV = (float) (i % 30000L) / 30000.0F;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(transformU(u, v), transformV(u, v));
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v,
                          int overlay, int light, float normalX, float normalY, float normalZ) {
        delegate.addVertex(x, y, z, color, transformU(u, v), transformV(u, v),
                overlay, light, normalX, normalY, normalZ);
    }

    private float transformU(float u, float v) {
        return (u * SCALE) * COS - (v * SCALE) * SIN + offsetU;
    }

    private float transformV(float u, float v) {
        return (u * SCALE) * SIN + (v * SCALE) * COS + offsetV;
    }
}
