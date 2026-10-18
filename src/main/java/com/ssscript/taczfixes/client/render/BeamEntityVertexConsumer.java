package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 激光束换用 NEW_ENTITY 渲染类型后补齐缺失元素。
 * NEW_ENTITY 的元素顺序是 pos -> color -> uv -> overlay -> light -> normal,
 * 光束只写 pos/color/uv/light, 因此 overlay 必须在 light 之前补, normal 在最后补。
 */
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
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        this.colorSet = true;
        delegate.color(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.uvSet = true;
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        this.overlaySet = true;
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int packedOverlay) {
        this.overlaySet = true;
        delegate.overlayCoords(packedOverlay);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        ensureBeforeLight();
        this.lightSet = true;
        delegate.uv2(u | 1, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int packedLight) {
        ensureBeforeLight();
        this.lightSet = true;
        delegate.uv2(packedLight | 1);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.normalSet = true;
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        ensureBeforeLight();
        if (!this.lightSet) {
            delegate.uv2(LightTexture.FULL_BRIGHT);
        }
        if (!this.normalSet) {
            delegate.normal(0.0f, 0.0f, 1.0f);
        }
        this.colorSet = false;
        this.uvSet = false;
        this.overlaySet = false;
        this.lightSet = false;
        this.normalSet = false;
        delegate.endVertex();
    }

    /** 必须在写 light 之前补齐 color/uv/overlay, 否则 BufferBuilder 的元素顺序会错位。 */
    private void ensureBeforeLight() {
        if (!this.colorSet) {
            this.colorSet = true;
            if (this.hasDefaultColor) {
                delegate.color(this.defaultR, this.defaultG, this.defaultB, this.defaultA);
            } else {
                delegate.color(255, 255, 255, 255);
            }
        }
        if (!this.uvSet) {
            this.uvSet = true;
            delegate.uv(0.0f, 0.0f);
        }
        if (!this.overlaySet) {
            this.overlaySet = true;
            delegate.overlayCoords(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF);
        }
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        this.hasDefaultColor = true;
        this.defaultR = r;
        this.defaultG = g;
        this.defaultB = b;
        this.defaultA = a;
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        this.hasDefaultColor = false;
        delegate.unsetDefaultColor();
    }
}
