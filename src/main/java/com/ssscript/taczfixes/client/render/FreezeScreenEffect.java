package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.util.FreezeClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;

/**
 * 常凪冻结的屏幕效果: 冻结瞬间把当前画面拷贝成快照, 冻结期间用快照覆盖屏幕(画面真正静止),
 * 并在四周叠加平滑的红色径向暗角; 解冻时快照与红边一起淡出。
 * 在 renderLevel 结束时绘制, 只作用于世界/手部, HUD 保持正常绘制。
 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FreezeScreenEffect {
    private static ShaderInstance shader;
    private static AbstractUniform uFreezeOpacity;
    private static AbstractUniform uRedStrength;
    private static RenderTarget snapshot;
    private static VertexBuffer quad;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static long lastNanos;
    private static float overlayAlpha;
    private static float redStrength;
    private static boolean captured;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        release();
        shader = null;
        uFreezeOpacity = null;
        uRedStrength = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "freeze_screen"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    shader = s;
                    uFreezeOpacity = s.safeGetUniform("FreezeOpacity");
                    uRedStrength = s.safeGetUniform("RedStrength");
                });
    }

    /** 当前冻结画面覆盖强度(含淡入/淡出), 0 表示完全没有冻结效果。 */
    public static float overlayAlpha() {
        return overlayAlpha;
    }

    /** 在 renderLevel 结束时调用(压制效果之后): 冻结期间画面静止 + 红色边缘, 解冻淡出。 */
    public static void composite() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dtMs = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1.0e6F;
        lastNanos = now;
        dtMs = Mth.clamp(dtMs, 0.0F, 100.0F);
        boolean frozen = FreezeClientState.isFrozen() && mc.player != null && mc.level != null && !mc.isPaused();
        if (frozen) {
            overlayAlpha = Math.min(1.0F, overlayAlpha + dtMs / 120.0F);
            redStrength = Math.min(1.0F, redStrength + dtMs / 160.0F);
        } else {
            // 解冻后快照与红边一起淡出。
            overlayAlpha = Math.max(0.0F, overlayAlpha - dtMs / 420.0F);
            redStrength = Math.max(0.0F, redStrength - dtMs / 420.0F);
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) {
            return;
        }
        ensureSnapshot(main);
        if (frozen && !captured) {
            capture(main);
        }
        if (snapshot == null || shader == null || (overlayAlpha <= 0.001F && redStrength <= 0.001F)) {
            if (!frozen) {
                captured = false;
            }
            return;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int previousTexture = RenderSystem.getShaderTexture(0);
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            ensureQuad();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            PbrRenderer.setUniform(uFreezeOpacity, overlayAlpha);
            PbrRenderer.setUniform(uRedStrength, redStrength);
            shader.setSampler("Sampler0", snapshot.getColorTextureId());
            main.bindWrite(true);
            quad.bind();
            shader.apply();
            // apply() 之后重新设置混合, 避免被着色器缓存/其它状态覆盖。
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            try {
                quad.draw();
            } finally {
                shader.clear();
            }
        } finally {
            VertexBuffer.unbind();
            main.bindWrite(true);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShader(() -> previousShader);
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.depthMask(depthMask);
        }
    }

    /** 把当前画面拷贝进快照纹理(借用 PBR 模糊着色器: 方向为 0 时就是原样采样)。 */
    private static void capture(RenderTarget main) {
        if (PbrRenderer.blurShader == null) {
            return;
        }
        ensureSnapshot(main);
        if (snapshot == null) {
            return;
        }
        int previousTexture = RenderSystem.getShaderTexture(0);
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            ensureQuad();
            PbrRenderer.setBlurDirection(0.0F, 0.0F);
            snapshot.bindWrite(true);
            RenderSystem.setShaderTexture(0, main.getColorTextureId());
            RenderSystem.setShader(() -> PbrRenderer.blurShader);
            quad.bind();
            quad.drawWithShader(IDENTITY, IDENTITY, PbrRenderer.blurShader);
            captured = true;
        } finally {
            VertexBuffer.unbind();
            main.bindWrite(true);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void ensureSnapshot(RenderTarget main) {
        if (snapshot != null && snapshot.width == main.width && snapshot.height == main.height) {
            return;
        }
        if (snapshot != null) {
            snapshot.destroyBuffers();
            snapshot = null;
        }
        if (quad != null) {
            quad.close();
            quad = null;
        }
        snapshot = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        snapshot.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        snapshot.setFilterMode(GL11.GL_LINEAR);
        captured = false;
    }

    private static void ensureQuad() {
        if (quad != null) {
            return;
        }
        BufferBuilder b = new BufferBuilder(128);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1, -1, 0).uv(0, 0).endVertex();
        b.vertex(1, -1, 0).uv(1, 0).endVertex();
        b.vertex(1, 1, 0).uv(1, 1).endVertex();
        b.vertex(-1, 1, 0).uv(0, 1).endVertex();
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(b.end());
    }

    private static void release() {
        if (snapshot != null) {
            snapshot.destroyBuffers();
            snapshot = null;
        }
        if (quad != null) {
            quad.close();
            quad = null;
        }
    }

    private FreezeScreenEffect() {
    }
}