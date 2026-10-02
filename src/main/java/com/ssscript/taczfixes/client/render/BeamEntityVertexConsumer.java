package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 激光束换用 NEW_ENTITY 渲染类型后, TaCZ 只写 position/color/uv/uv2。
 * 本包装器补齐 overlay/normal 并把光照低位置 1 作为自发光标记。
 */
public final class BeamEntityVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private boolean overlaySet;
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
        delegate.color(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
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
        delegate.uv2(u | 1, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int packedLight) {
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
        if (!this.overlaySet) {
            delegate.overlayCoords(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF);
        }
        if (!this.normalSet) {
            delegate.normal(0.0f, 0.0f, 1.0f);
        }
        this.overlaySet = false;
        this.normalSet = false;
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
