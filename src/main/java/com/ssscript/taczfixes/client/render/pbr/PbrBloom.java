package com.ssscript.taczfixes.client.render.pbr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** Separate world/hand phases prevent the hand depth clear from invalidating world bloom. */
public final class PbrBloom {
    private static RenderTarget emission, horizontal, vertical;
    private static VertexBuffer quad;
    private static boolean active, dirty;
    private static final Matrix4f IDENTITY = new Matrix4f();

    public static void beginFrame() {
        active = true;
        dirty = false;
        if (!PbrRenderer.enabled() || !Config.PBR_BLOOM.get()) {
            release();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    public static boolean canCapture() {
        return active && PbrRenderer.enabled() && Config.PBR_BLOOM.get()
                && Config.PBR_EMISSION.get() > 0 && Config.PBR_BLOOM_STRENGTH.get() > 0
                && PbrRenderer.blurShader != null && PbrRenderer.compositeShader != null;
    }

    public static void bindCapture() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (emission == null || emission.width != main.width || emission.height != main.height
                || emission.isStencilEnabled() != main.isStencilEnabled()) {
            releaseTargets();
            emission = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) emission.enableStencil();
            emission.setClearColor(0, 0, 0, 0);
            emission.setFilterMode(GL11.GL_LINEAR);
            // Keep source rows: nearest half-resolution sampling can drop thin sights entirely.
            horizontal = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            vertical = new TextureTarget(horizontal.width, horizontal.height, false, Minecraft.ON_OSX);
            horizontal.setFilterMode(GL11.GL_LINEAR);
            vertical.setFilterMode(GL11.GL_LINEAR);
            dirty = false;
        }
        if (!dirty) {
            // The caller enables depth writes before clearing this target.
            emission.clear(Minecraft.ON_OSX);
            dirty = true;
        }
        if (main.isStencilEnabled()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, emission.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
        }
        emission.bindWrite(true);
        RenderSystem.setShaderTexture(5, main.getDepthTextureId());
    }

    public static void endFrame() {
        try { composite(); } finally { active = false; dirty = false; }
    }

    public static void checkCapture() { PbrDiagnostics.capture(emission); }

    public static void composite() {
        if (!dirty || !canCapture()) { dirty = false; return; }
        var main = Minecraft.getInstance().getMainRenderTarget();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        PbrBlendCache.save();
        int[] textures = new int[3];
        for (int i = 0; i < textures.length; i++) textures[i] = RenderSystem.getShaderTexture(i);
        ShaderInstance previous = RenderSystem.getShader();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            ensureQuad();
            float radius = Config.PBR_BLOOM_RADIUS.get().floatValue();
            RenderSystem.setShaderTexture(1, emission.getDepthTextureId());
            RenderSystem.setShaderTexture(2, main.getDepthTextureId());
            PbrRenderer.blurShader.safeGetUniform("CheckDepth").set(1);
            PbrRenderer.blurShader.safeGetUniform("Direction").set(radius / main.width, 0f);
            draw(emission, horizontal, PbrRenderer.blurShader);
            PbrRenderer.blurShader.safeGetUniform("CheckDepth").set(0);
            PbrRenderer.blurShader.safeGetUniform("Direction").set(0f, radius / main.height);
            draw(horizontal, vertical, PbrRenderer.blurShader);
            PbrDiagnostics.blurred(vertical);
            PbrRenderer.compositeShader.safeGetUniform("Strength").set(Config.PBR_BLOOM_STRENGTH.get().floatValue());
            // ShaderInstance caches BlendMode. If its additive mode is already cached,
            // apply() will not undo the disableBlend() above, and the quad replaces the world.
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            main.bindWrite(true);
            PbrDiagnostics.beforeComposite(main);
            compositeToMain(vertical, PbrRenderer.compositeShader);
            PbrDiagnostics.afterComposite(main);
        } finally {
            dirty = false;
            VertexBuffer.unbind();
            main.bindWrite(true);
            for (int i = 0; i < textures.length; i++) RenderSystem.setShaderTexture(i, textures[i]);
            RenderSystem.setShader(() -> previous);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            // Restoring GL alone leaves ShaderInstance's BlendMode cache pointing at
            // our additive pass. The next vanilla HUD shader would then override the
            // vignette's ZERO/ONE_MINUS_SRC_COLOR factors with alpha blending, painting
            // its opaque black/grey texture over the entire world.
            PbrBlendCache.restore();
            RenderSystem.depthMask(depthMask);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST);
        }
    }

    private static void compositeToMain(RenderTarget input, ShaderInstance shader) {
        // Apply the shader first, then establish the actual draw state. ShaderInstance
        // and third-party hooks may otherwise replace factors set before apply().
        shader.setSampler("Sampler0", input.getColorTextureId());
        quad.bind();
        shader.apply();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            quad.draw();
        } finally {
            shader.clear();
        }
    }

    private static void draw(RenderTarget input, RenderTarget output, ShaderInstance shader) {
        output.bindWrite(true);
        RenderSystem.setShaderTexture(0, input.getColorTextureId());
        RenderSystem.setShader(() -> shader);
        quad.bind();
        quad.drawWithShader(IDENTITY, IDENTITY, shader);
    }

    private static void ensureQuad() {
        if (quad != null) return;
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

    private static void releaseTargets() {
        if (emission != null) emission.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (vertical != null) vertical.destroyBuffers();
        emission = horizontal = vertical = null;
        dirty = false;
    }

    public static void release() {
        releaseTargets();
        if (quad != null) { quad.close(); quad = null; }
    }

    private PbrBloom() {}
}
