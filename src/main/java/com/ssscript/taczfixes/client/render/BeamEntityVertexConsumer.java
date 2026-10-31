package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class BeamEntityVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private boolean colorSet;
    private boolean hasDefaultColor;
    private int defaultR = 255;
    private int defaultG = 255;
    private int defaultB = 255;
    private int defaultA = 255;
    private boolean uvSet;
    private boolean overlaySet;
    private boolean lightSet;
    private boolean normalSet;

    public BeamEntityVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.colorSet = false;
        this.uvSet = false;
        this.overlaySet = false;
        this.lightSet = false;
        this.normalSet = false;
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        this.colorSet = true;
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.uvSet = true;
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.overlaySet = true;
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        ensureBeforeLight();
        this.lightSet = true;
        delegate.setUv2(u | 1, v);
        if (!this.normalSet) {
            this.normalSet = true;
            delegate.setNormal(0.0f, 0.0f, 1.0f);
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.normalSet = true;
        delegate.setNormal(x, y, z);
        return this;
    }

    private void ensureBeforeLight() {
        if (!this.colorSet) {
            this.colorSet = true;
            if (this.hasDefaultColor) {
                delegate.setColor(this.defaultR, this.defaultG, this.defaultB, this.defaultA);
            } else {
                delegate.setColor(255, 255, 255, 255);
            }
        }
        if (!this.uvSet) {
            this.uvSet = true;
            delegate.setUv(0.0f, 0.0f);
        }
        if (!this.overlaySet) {
            this.overlaySet = true;
            delegate.setUv1(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF);
        }
    }

    public void setLightIfMissing() {
        if (!this.lightSet) {
            this.lightSet = true;
            delegate.setLight(LightTexture.FULL_BRIGHT);
        }
    }
}
