package com.ssscript.taczfixes.client.render.pbr;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Marks TaCZ illuminated groups in the otherwise unused low lightmap bit. */
public final class IlluminatedVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;

    public IlluminatedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
    @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
    @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u | 1, v); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    @Override public void endVertex() { delegate.endVertex(); }
    @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
    @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}
