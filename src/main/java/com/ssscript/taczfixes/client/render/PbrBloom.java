package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.client.handler.AimSwayHandler;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;


/** Separate world/hand phases prevent the hand depth clear from invalidating world bloom. */
public final class PbrBloom {
    private static RenderTarget emission, horizontal, vertical, scopeMask;
    private static VertexBuffer quad, maskQuad;
    private static boolean active, dirty, stencilCopied, scopeMaskValid;
    private static final Matrix4f IDENTITY = new Matrix4f();

    public static void beginFrame() {
        active = true;
        dirty = false;
        stencilCopied = false;
        scopeMaskValid = false;
        PbrRenderer.resetScopeDivision();
        if (!PbrRenderer.enabled() || !Config.PBR_BLOOM.get()) {
            release();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
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
        } else {
        }
        if (main.isStencilEnabled() && (!stencilCopied || pureScopeAiming()
                || GL11.glIsEnabled(GL11.GL_STENCIL_TEST))) {
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
        try {
            composite();
        } finally {
            active = false;
            dirty = false;
        }
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
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            ensureQuad();
            float radius = Config.PBR_BLOOM_RADIUS.get().floatValue();
            // Match 2.0.0: check scene depth only on the first blur pass.
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
            int maskTexture = scopeMaskValid && scopeMask != null ? scopeMask.getColorTextureId() : 0;
            compositeToMain(horizontal, PbrRenderer.compositeShader, maskTexture);
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

    private static void compositeToMain(RenderTarget input, ShaderInstance shader, int maskTexture) {
        // Apply the shader first, then establish the actual draw state. ShaderInstance
        // and third-party hooks may otherwise replace factors set before apply().
        shader.setSampler("Sampler0", input.getColorTextureId());
        shader.setSampler("ScopeMask", maskTexture);
        shader.setSampler("EmissionDepth", emission.getDepthTextureId());
        shader.setSampler("SceneDepth", Minecraft.getInstance().getMainRenderTarget().getDepthTextureId());
        PbrRenderer.setHasScopeMask(maskTexture != 0 ? 1 : 0);
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
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(-1, -1, 0).setUv(0, 0);
        b.addVertex(1, -1, 0).setUv(1, 0);
        b.addVertex(1, 1, 0).setUv(1, 1);
        b.addVertex(-1, 1, 0).setUv(0, 1);
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(b.buildOrThrow());
    }

    private static void releaseTargets() {
        if (emission != null) emission.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (vertical != null) vertical.destroyBuffers();
        emission = horizontal = vertical = null;
        releaseScopeMask();
        dirty = false;
        stencilCopied = false;
    }

    public static void release() {
        releaseTargets();
        if (quad != null) { quad.close(); quad = null; }
        if (maskQuad != null) { maskQuad.close(); maskQuad = null; }
    }

    public static boolean pureScopeAiming() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isAlive()) return false;
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null || operator.getClientAimingProgress(mc.getTimer().getGameTimeDeltaPartialTick(false)) < 0.01f) return false;
        // Only the currently active scope view occludes bloom. A sight (or the
        // sight view of a combined optic) must keep its own bloom.
        if (!AimSwayHandler.isScopeActive(player)) return false;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;
        ResourceLocation id = activeScopeId(iGun, gun);
        return id != null && TimelessAPI.getClientAttachmentIndex(id).isPresent();
    }

    /** 褰撳墠娓叉煋鐨勯厤浠舵槸鍚︿负姝ｅ湪浣跨敤鐨?scope 瑙嗗浘鐬勫叿銆?*/
    public static boolean isActiveScopeItem(ItemStack attachment, ItemStack gun) {
        if (attachment == null || attachment.isEmpty() || gun == null || gun.isEmpty()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ItemStack.isSameItemSameComponents(player.getMainHandItem(), gun)) return false;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iAttachment == null || iGun == null) return false;
        ResourceLocation id = iAttachment.getAttachmentId(attachment);
        ResourceLocation active = activeScopeId(iGun, gun);
        return id != null && active != null && id.equals(active);
    }

    private static ResourceLocation activeScopeId(IGun iGun, ItemStack gun) {
        ResourceLocation id = ScopeSwitchState.attachmentId(iGun, gun, AttachmentType.SCOPE);
        if (id == null || DefaultAssets.isEmptyAttachmentId(id)) {
            id = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
        }
        return id == null || DefaultAssets.isEmptyAttachmentId(id) ? null : id;
    }

    public static boolean scopeStencilAvailable() {
        return Minecraft.getInstance().getMainRenderTarget().isStencilEnabled();
    }

    /** 鎶婂綋鍓?scope 闀滃唴鍖哄煙(division 闄ゅ)璁板綍涓洪伄缃? 娉涘厜鍚堟垚鏃跺墧闄ら暅鍐呮硾鍏夈€?*/
    public static void captureScopeMask() {
        if (!pureScopeAiming()) {
            scopeMaskValid = false;
            return;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (!main.isStencilEnabled()
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId) {
            scopeMaskValid = false;
            return;
        }
        int width = main.width;
        int height = main.height;
        if (scopeMask == null || scopeMask.width != width || scopeMask.height != height
                || !scopeMask.isStencilEnabled()) {
            releaseScopeMask();
            scopeMask = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            scopeMask.enableStencil();
            scopeMask.setClearColor(0, 0, 0, 0);
            scopeMask.setFilterMode(GL11.GL_LINEAR);
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int[] viewport = new int[4];
        int[] scissorBox = new int[4];
        float[] clearColor = new float[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        PbrBlendCache.save();
        ShaderInstance previous = RenderSystem.getShader();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scopeMask.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, scopeMask.width, scopeMask.height,
                    GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST);
            scopeMask.bindWrite(true);
            RenderSystem.disableScissor();
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_NOTEQUAL, 0, 255);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            ensureMaskQuad();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            ShaderInstance shader = RenderSystem.getShader();
            maskQuad.bind();
            maskQuad.drawWithShader(IDENTITY, IDENTITY, shader);
            VertexBuffer.unbind();
        } finally {
            main.bindWrite(true);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissor) {
                RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            } else {
                RenderSystem.disableScissor();
            }
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.setShader(() -> previous);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            PbrBlendCache.restore();
            RenderSystem.depthMask(depthMask);
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        }
        scopeMaskValid = true;
    }

    private static void ensureMaskQuad() {
        if (maskQuad != null) return;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(-1, -1, 0).setColor(255, 255, 255, 255);
        b.addVertex(1, -1, 0).setColor(255, 255, 255, 255);
        b.addVertex(1, 1, 0).setColor(255, 255, 255, 255);
        b.addVertex(-1, 1, 0).setColor(255, 255, 255, 255);
        maskQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        maskQuad.bind();
        maskQuad.upload(b.buildOrThrow());
    }

    private static void releaseScopeMask() {
        if (scopeMask != null) {
            scopeMask.destroyBuffers();
            scopeMask = null;
        }
        scopeMaskValid = false;
    }

    private PbrBloom() {}
}
