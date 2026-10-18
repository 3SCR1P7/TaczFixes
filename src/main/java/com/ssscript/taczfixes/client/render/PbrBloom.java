package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
    private static boolean active, dirty, stencilCopied;
    private static boolean emissiveThisFrame;
    private static final Matrix4f IDENTITY = new Matrix4f();

    public static void beginFrame() {
        active = true;
        dirty = false;
        stencilCopied = false;
        emissiveThisFrame = false;
        if (!PbrRenderer.enabled() || !Config.PBR_BLOOM.get()) {
            release();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    /** 本帧写入了可能发光的顶点(illuminated 组、镭射光束等)。 */
    public static void markEmissive() {
        emissiveThisFrame = true;
    }

    public static boolean hasEmissiveContent() {
        return emissiveThisFrame;
    }

    public static boolean canCapture() {
        return active && PbrRenderer.enabled() && Config.PBR_BLOOM.get()
                && Config.PBR_EMISSION.get() > 0 && Config.PBR_BLOOM_STRENGTH.get() > 0
                && PbrRenderer.blurShader != null && PbrRenderer.compositeShader != null
                && PbrRenderer.maskShader != null;
    }

    public static void bindCapture() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        int width = scaledSize(main.width);
        int height = scaledSize(main.height);
        if (emission == null || emission.width != width || emission.height != height
                || emission.isStencilEnabled() != main.isStencilEnabled()) {
            releaseTargets();
            emission = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            if (main.isStencilEnabled()) emission.enableStencil();
            emission.setClearColor(0, 0, 0, 0);
            emission.setFilterMode(GL11.GL_LINEAR);
            // Bloom runs below main resolution; linear upsampling keeps it smooth while
            // cutting both fill rate and blur taps. Direction is normalized by main size,
            // so the on-screen radius is unchanged.
            horizontal = new TextureTarget(width, height, false, Minecraft.ON_OSX);
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
        if (main.isStencilEnabled() && (!stencilCopied || pureScopeAiming())) {
            // 全屏 stencil 拷贝每帧只做一次; 之前每个 PBR mesh 都拷贝一次。
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, emission.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, emission.width, emission.height,
                    GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
            stencilCopied = true;
        }
        emission.bindWrite(true);
        RenderSystem.setShaderTexture(5, main.getDepthTextureId());
    }

    private static int scaledSize(int mainSize) {
        double scale = Config.PBR_BLOOM_RESOLUTION.get();
        if (scale > 1.0) scale = 1.0;
        if (scale < 0.125) scale = 0.125;
        return Math.max(1, (int) Math.round(mainSize * scale));
    }

    public static void endFrame() {
        try { composite(); } finally { active = false; dirty = false; }
    }

    public static void composite() {
        if (!dirty || !canCapture()) { dirty = false; return; }
        var main = Minecraft.getInstance().getMainRenderTarget();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
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
            // 纯 scope 瞄具: 泛光合成也要遵守镜内遮挡模板, 否则被剔除的枪械/配件自发光仍会泛光到镜内。
            if (pureScopeAiming()) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(GL11.GL_EQUAL, 0, 255);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            ensureQuad();
            float radius = Config.PBR_BLOOM_RADIUS.get().floatValue();
            // Resolve per-pixel occlusion once, then blur color only.
            RenderSystem.setShaderTexture(0, emission.getColorTextureId());
            RenderSystem.setShaderTexture(1, emission.getDepthTextureId());
            RenderSystem.setShaderTexture(2, main.getDepthTextureId());
            draw(emission, horizontal, PbrRenderer.maskShader);
            RenderSystem.setShaderTexture(1, 0);
            RenderSystem.setShaderTexture(2, 0);
            PbrRenderer.setBlurDirection(radius / main.width, 0f);
            draw(horizontal, vertical, PbrRenderer.blurShader);
            PbrRenderer.setBlurDirection(0f, radius / main.height);
            draw(vertical, horizontal, PbrRenderer.blurShader);
            PbrRenderer.setBloomStrength(Config.PBR_BLOOM_STRENGTH.get().floatValue());
            // ShaderInstance caches BlendMode. If its additive mode is already cached,
            // apply() will not undo the disableBlend() above, and the quad replaces the world.
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            main.bindWrite(true);
            compositeToMain(horizontal, PbrRenderer.compositeShader);
        } finally {
            dirty = false;
            stencilCopied = false;
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
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilMask);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
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
        stencilCopied = false;
    }

    public static void release() {
        releaseTargets();
        if (quad != null) { quad.close(); quad = null; }
    }

    public static boolean pureScopeAiming() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || operator.getClientAimingProgress(mc.getFrameTime()) < 0.01f) return false;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) return false;
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
        return index != null && index.isScope() && !index.isSight();
    }
    private PbrBloom() {}
}
