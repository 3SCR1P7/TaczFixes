package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 激光束换用 NEW_ENTITY 渲染类型后, 部分光束实现只写 pos/color/uv 的子集。
 * 本包装器补齐缺失的 color/uv/overlay/light/normal, 避免 BufferBuilder 报
 * "Not filled all elements of the vertex"。
 */
public final class BeamEntityVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private boolean colorSet;
    private boolean hasDefaultColor;
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
        this.lightSet = true;
        delegate.uv2(u | 1, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int packedLight) {
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
        if (!this.colorSet && !this.hasDefaultColor) {
            delegate.color(255, 255, 255, 255);
        }
        if (!this.uvSet) {
            delegate.uv(0.0f, 0.0f);
        }
        if (!this.overlaySet) {
            delegate.overlayCoords(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF);
        }
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

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        this.hasDefaultColor = true;
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        this.hasDefaultColor = false;
        delegate.unsetDefaultColor();
    }
}
