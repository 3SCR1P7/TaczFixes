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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;


/**
 * 枪械自发光泛光(MRT 实现)。
 *
 * <p>发光不再单独画一遍几何: PBR 着色器在主绘制时把 {@code glow + glare} 和发光像素深度
 * 写进主渲染目标的第二/第三颜色附件(全分辨率), 泛光链路只做遮罩、模糊和合成:</p>
 * <ol>
 *   <li>PBR 主绘制: 同一次着色同时写出主颜色、发光颜色、发光像素的打包深度;</li>
 *   <li>可见性遮罩: 用发光深度和最终场景深度比较, 被后画的前景(第一人称手臂、瞄具玻璃等)挡住的部分
 *       不参与泛光, 所以手臂能挡住泛光;</li>
 *   <li>金字塔高斯: 遮罩结果在半分辨率/四分之一分辨率上模糊(光晕是低频信号, 与全分辨率模糊一致),
 *       最后全分辨率合成。</li>
 * </ol>
 */
public final class PbrBloom {
    private static RenderTarget emission, emissionDepth, masked, narrowA, narrowB, wideA, wideB, scopeMask;
    private static RenderTarget smoothNarrow, smoothWide;
    private static VertexBuffer quad, maskQuad;
    private static boolean active, dirty, scopeMaskValid;
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** 宽层高斯核相对窄层的倍数。旧实现是"窄层结果再乘 2.4 倍半径", 总 sigma = sqrt(1 + 2.4^2) ≈ 2.6。 */
    private static final float WIDE_SIGMA_FACTOR = 2.6f;

    /** 发光附件当前挂在哪个主 FBO 上(主目标重建后需要重新挂)。 */
    private static int attachedFrameBufferId = -1;
    /** MRT 不可用时(驱动/FBO 不完整)退回"单独补画一次发光", 保证不崩、不丢泛光。 */
    private static boolean mrtUnavailable;
    private static int fallbackFbo = -1;
    private static boolean fallbackUnavailable;

    private static final int[] MRT_DRAW_BUFFERS = {
            GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2};
    private static final int[] FALLBACK_DRAW_BUFFERS = {
            GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1};
    private static final int[] SINGLE_DRAW_BUFFER = {GL30.GL_COLOR_ATTACHMENT0};

    /** 泛光链路隔帧更新: 光环是低频信号, 30Hz 更新几乎看不出, 但整条链路(含发光 MRT 写入)开销减半。 */
    private static boolean buildThisFrame;
    private static boolean chainValid;
    private static boolean smoothValid;
    private static long bloomFrameCounter;

    public static void beginFrame() {
        PbrRenderer.beginFrame();
        active = true;
        dirty = false;
        scopeMaskValid = false;
        PbrRenderer.resetScopeDivision();
        int interval = Math.max(1, Config.PBR_BLOOM_UPDATE_INTERVAL.get());
        buildThisFrame = (bloomFrameCounter++ % interval) == 0L;
        if (!PbrRenderer.enabled() || !Config.PBR_BLOOM.get()) {

            release();
            chainValid = false;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    /** 本帧是否在构建泛光链路(隔帧更新时为 false, 合成继续复用上一帧结果)。 */
    public static boolean isBuildingEmission() {
        return buildThisFrame;
    }

    /** 泛光配置与着色器是否就绪(与当前帧有没有自发光内容无关)。 */
    static boolean configured() {
        return active && PbrRenderer.enabled() && Config.PBR_BLOOM.get()
                && Config.PBR_EMISSION.get() > 0 && Config.PBR_BLOOM_STRENGTH.get() > 0
                && PbrRenderer.blurShader != null && PbrRenderer.compositeShader != null
                && PbrRenderer.maskShader != null;
    }

    /**
     * 本帧是否值得跑泛光链路: 配置就绪, 且这一帧真的画了自发光内容。
     * 没有自发光(枪身没有 *_illuminated 骨骼、_s 贴图 alpha 全 0/255、没有镭射/曳光/发光迷彩)时,
     * 第二/第三颜色附件不启用、遮罩/模糊/合成全部跳过, 不产生任何额外开销。
     */
    public static boolean canCapture() {
        return configured() && PbrRenderer.hasEmissionSource();
    }

    /**
     * 主绘制前调用: 让接下来这次绘制同时写出"发光颜色 + 发光深度"(绑定到主目标的第二/第三颜色附件)。
     * 不需要时返回 false, 调用方按普通单目标绘制即可。
     */
    public static boolean beginEmissionWrite(boolean mainTarget) {
        if (!mainTarget || !canCapture()) {
            return false;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        ensureTargets(main);
        if (!ensureAttached(main)) {
            return false;
        }
        if (!dirty) {
            clearEmissionTargets(main);
            dirty = true;
        }
        GL30.glDrawBuffers(MRT_DRAW_BUFFERS);
        return true;
    }

    /** 主绘制后调用: 恢复单颜色附件绘制状态。 */
    public static void endEmissionWrite() {
        GL30.glDrawBuffers(SINGLE_DRAW_BUFFER);
    }

    /** MRT 是否不可用(驱动/FBO 不完整等); 不可用时退回单独补画一次发光。 */
    public static boolean mrtUnavailable() {
        return mrtUnavailable;
    }

    /**
     * 加速渲染补画(AR 加速时主绘制被跳过, 没有 MRT 写入)、或 MRT 不可用时的补画: 绑定补画 FBO,
     * 只把发光颜色和发光深度画进两个附件, 不碰主画面。
     */
    public static boolean beginFallbackCapture() {
        if (!canCapture()) {
            return false;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        ensureTargets(main);
        if (!dirty) {
            clearEmissionTargets(main);
            dirty = true;
        }
        if (!ensureFallbackFbo()) {
            return false;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fallbackFbo);
        GL30.glDrawBuffers(FALLBACK_DRAW_BUFFERS);
        return true;
    }

    private static void clearEmissionTargets(RenderTarget main) {
        // 用各自的 FBO 清色+深度, 再把主目标绑回来。
        emission.bindWrite(true);
        emission.clear(Minecraft.ON_OSX);
        emissionDepth.bindWrite(true);
        emissionDepth.clear(Minecraft.ON_OSX);
        main.bindWrite(true);
    }

    private static void ensureTargets(RenderTarget main) {
        int halfW = scaledSize(main.width, narrowScale());
        int halfH = scaledSize(main.height, narrowScale());
        int quarterW = scaledSize(main.width, wideScale());
        int quarterH = scaledSize(main.height, wideScale());
        if (emission != null && emissionDepth != null && masked != null
                && emission.width == main.width && emission.height == main.height
                && masked.width == halfW && masked.height == halfH
                && narrowA.width == halfW && smoothNarrow != null && smoothNarrow.width == halfW
                && wideA.width == quarterW && wideA.height == quarterH
                && smoothWide != null && smoothWide.width == quarterW) {
            return;
        }
        releaseTargets();
        emission = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        emission.setClearColor(0, 0, 0, 0);
        emission.setFilterMode(GL11.GL_LINEAR);
        emissionDepth = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        emissionDepth.setClearColor(0, 0, 0, 0);
        emissionDepth.setFilterMode(GL11.GL_NEAREST);
        // 可见性遮罩按"窄层"分辨率(默认半分辨率)做: 泛光本身是低频信号, 遮罩边缘略粗看不出,
        // 但遮罩 pass 的像素数和写带宽直接降到 1/4。
        masked = new TextureTarget(halfW, halfH, false, Minecraft.ON_OSX);
        masked.setClearColor(0, 0, 0, 0);
        masked.setFilterMode(GL11.GL_LINEAR);
        narrowA = new TextureTarget(halfW, halfH, false, Minecraft.ON_OSX);
        narrowB = new TextureTarget(halfW, halfH, false, Minecraft.ON_OSX);
        wideA = new TextureTarget(quarterW, quarterH, false, Minecraft.ON_OSX);
        wideB = new TextureTarget(quarterW, quarterH, false, Minecraft.ON_OSX);
        smoothNarrow = new TextureTarget(halfW, halfH, false, Minecraft.ON_OSX);
        smoothWide = new TextureTarget(quarterW, quarterH, false, Minecraft.ON_OSX);
        RenderTarget[] targets = {masked, narrowA, narrowB, wideA, wideB, smoothNarrow, smoothWide};
        for (RenderTarget target : targets) {
            target.setClearColor(0, 0, 0, 0);
            target.setFilterMode(GL11.GL_LINEAR);
        }
    }

    /** 把发光颜色/发光深度挂到主 FBO 的 COLOR_ATTACHMENT1/2 上(主目标重建后自动重挂)。 */
    private static boolean ensureAttached(RenderTarget main) {
        if (mrtUnavailable || emission == null || emissionDepth == null) {
            return false;
        }
        if (attachedFrameBufferId == main.frameBufferId) {
            return true;
        }
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                    GL11.GL_TEXTURE_2D, emission.getColorTextureId(), 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                    GL11.GL_TEXTURE_2D, emissionDepth.getColorTextureId(), 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                detachFromMainTarget();
                mrtUnavailable = true;
                return false;
            }
            attachedFrameBufferId = main.frameBufferId;
            return true;
        } catch (Throwable throwable) {
            mrtUnavailable = true;
            return false;
        }
    }

    /** 补画 FBO: COLOR0 = 发光颜色, COLOR1 = 发光打包深度, 深度附件用发光目标的深度。 */
    private static boolean ensureFallbackFbo() {
        if (fallbackFbo >= 0) {
            return true;
        }
        if (fallbackUnavailable || emission == null || emissionDepth == null) {
            return false;
        }
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            int fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, emission.getColorTextureId(), 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                    GL11.GL_TEXTURE_2D, emissionDepth.getColorTextureId(), 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, emission.getDepthTextureId(), 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                GL30.glDeleteFramebuffers(fbo);
                fallbackUnavailable = true;
                return false;
            }
            GL30.glDrawBuffers(FALLBACK_DRAW_BUFFERS);
            fallbackFbo = fbo;
            return true;
        } catch (Throwable throwable) {
            fallbackUnavailable = true;
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
    }

    /** 窄层缓冲相对主分辨率的比例(0.5 × 质量系数)。 */
    private static double narrowScale() {
        return 0.5D * quality();
    }

    /** 宽层缓冲相对主分辨率的比例(0.25 × 质量系数)。 */
    private static double wideScale() {
        return 0.25D * quality();
    }

    private static double quality() {
        double value = Config.PBR_BLOOM_RESOLUTION.get();
        if (value > 1.0D) value = 1.0D;
        if (value < 0.25D) value = 0.25D;
        return value;
    }

    private static int scaledSize(int mainSize, double scale) {
        return Math.max(1, (int) Math.round(mainSize * scale));
    }

    public static void endFrame() {
        try {
            verifyAcceleratedEmission();
            composite();
        } finally {
            active = false;
            dirty = false;
        }
    }

    private static java.nio.ByteBuffer verifyBuffer;

    /**
     * 回读发光附件, 确认加速渲染的主绘制真的把发光写进去了(alpha = 1 的位置)。
     * 验证通过后 {@link PbrRenderer#shouldReplayEmission} 才不再为泛光补画整个模型;
     * 验证不通过就继续走补画, 观感与以前一致, 不会出现"开了 AR 就没泛光"。
     */
    private static void verifyAcceleratedEmission() {
        if (emission == null || !PbrRenderer.requestAcceleratedEmissionVerify()) {
            return;
        }
        int width = emission.width;
        int height = emission.height;
        if (width <= 0 || height <= 0) {
            return;
        }
        int bandHeight = Math.max(1, Math.min(64, height / 8));
        int size = width * bandHeight * 4;
        java.nio.ByteBuffer buffer = verifyBuffer;
        if (buffer == null || buffer.capacity() < size) {
            buffer = BufferUtils.createByteBuffer(size);
            verifyBuffer = buffer;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, emission.frameBufferId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            for (int band = 0; band < 4; band++) {
                int y = (int) ((long) height * (band + 1) / 5) - bandHeight / 2;
                y = Math.max(0, Math.min(height - bandHeight, y));
                buffer.clear();
                GL11.glReadPixels(0, y, width, bandHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                for (int i = 3; i < size; i += 4) {
                    if (buffer.get(i) != 0) {
                        PbrRenderer.confirmAcceleratedEmission();
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    private static final int[] SCISSOR_RECT = new int[4];
    private static final java.nio.IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(4);
    private static boolean scissorActive;
    private static boolean scissorWasEnabled;
    /** 裁剪矩形相对发光包围盒的膨胀量(主分辨率像素): 覆盖两级模糊尾巴。 */
    private static float scissorMarginPixels = 96f;

    /** 开始把泛光 pass 限制在发光包围盒内; 恢复逻辑在 composite() 的 finally 里。 */
    private static void beginPassScissor() {
        scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        SCISSOR_BOX.clear();
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
        scissorActive = true;
    }

    private static void endPassScissor() {
        if (!scissorActive) {
            return;
        }
        scissorActive = false;
        if (scissorWasEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(SCISSOR_BOX.get(0), SCISSOR_BOX.get(1), SCISSOR_BOX.get(2), SCISSOR_BOX.get(3));
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    /** 对当前目标启用 scissor; 没有发光包围盒时退回全屏。返回是否真的启用了裁剪。 */
    private static boolean applyPassScissor(RenderTarget target) {
        if (!scissorActive || target == null) {
            return false;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || !PbrRenderer.emissionScissorRect(target.width, target.height,
                main.width, main.height, scissorMarginPixels, SCISSOR_RECT)) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return false;
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(SCISSOR_RECT[0], SCISSOR_RECT[1], SCISSOR_RECT[2], SCISSOR_RECT[3]);
        return true;
    }

    public static void composite() {
        if (!canCapture()) { dirty = false; return; }
        boolean build = buildThisFrame && dirty;
        if (build) {
            chainValid = true;
        }
        if (!chainValid) { dirty = false; return; }
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
            // 包围盒膨胀: 覆盖窄/宽两层高斯的可视尾部。
            scissorMarginPixels = radius * (float) WIDE_SIGMA_FACTOR * 2.0F + 96.0F;
            beginPassScissor();
            float strength = Config.PBR_BLOOM_STRENGTH.get().floatValue();
            boolean wide = wideB != null && Config.PBR_BLOOM_WIDE.get()
                    && Config.PBR_BLOOM_WIDE_STRENGTH.get() > 0.0D;
            if (build) {
                // 1) 可见性遮罩(半分辨率): 发光深度 vs 最终场景深度, 手臂等后画的前景会挡住泛光。
                RenderSystem.setShaderTexture(0, emission.getColorTextureId());
                RenderSystem.setShaderTexture(1, emissionDepth.getColorTextureId());
                RenderSystem.setShaderTexture(2, main.getDepthTextureId());
                draw(emission, masked, PbrRenderer.maskShader);
                RenderSystem.setShaderTexture(1, 0);
                RenderSystem.setShaderTexture(2, 0);
                // 2) 可分离高斯: 窄层半分辨率、宽层 1/4 分辨率。方向按主分辨率归一化, on-screen 半径不变。
                drawBlurPass(masked, narrowA, radius / main.width, 0f);
                drawBlurPass(narrowA, narrowB, 0f, radius / main.height);
                float wideSigma = radius * WIDE_SIGMA_FACTOR;
                if (wide) {
                    drawBlurPass(masked, wideA, wideSigma / main.width, 0f);
                    drawBlurPass(wideA, wideB, 0f, wideSigma / main.height);
                }
            }
            // 3) 更新间隔 > 1 时, 把最新链结果按指数平滑进两张常驻纹理, 合成始终采样平滑结果:
            //    这样光晕是逐帧连续变化的, 隔帧重建不会造成亮度/位置来回跳造成闪烁。
            int interval = Math.max(1, Config.PBR_BLOOM_UPDATE_INTERVAL.get());
            RenderTarget narrowSource = narrowB;
            RenderTarget wideSource = wide ? wideB : narrowB;
            if (interval > 1) {
                smoothBloom(narrowSource, wideSource, wide, smoothAlpha(interval));
                narrowSource = smoothNarrow;
                wideSource = wide ? smoothWide : smoothNarrow;
            }
            // 4) 全分辨率合成: 直接采样两张低分辨率模糊结果(线性放大), 加法混合到主画面。
            // ShaderInstance caches BlendMode. If its additive mode is already cached,
            // apply() will not undo the disableBlend() above, and the quad replaces the world.
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            main.bindWrite(true);
            int maskTexture = scopeMaskValid && scopeMask != null ? scopeMask.getColorTextureId() : 0;
            float wideStrength = wide ? strength * Config.PBR_BLOOM_WIDE_STRENGTH.get().floatValue() : 0f;
            compositeToMain(narrowSource, wideSource, PbrRenderer.compositeShader, maskTexture,
                    strength, wideStrength);
        } finally {
            dirty = false;
            endPassScissor();
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

    /** 指数平滑系数: 间隔越小越跟手, 间隔大时每条链结果占的权重也变小。 */
    private static float smoothAlpha(int interval) {
        return Math.max(0.2f, 1.0f / interval);
    }

    /** 把最新链结果按 alpha 混合进常驻平滑纹理(首帧 alpha=1 直接覆盖)。 */
    private static void smoothBloom(RenderTarget narrow, RenderTarget wide, boolean wideEnabled, float alpha) {
        float a = smoothValid ? alpha : 1.0f;
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        smoothPass(narrow, smoothNarrow, a);
        if (wideEnabled) {
            smoothPass(wide, smoothWide, a);
        }
        smoothValid = true;
    }

    private static void smoothPass(RenderTarget input, RenderTarget output, float alpha) {
        output.bindWrite(true);
        RenderSystem.setShaderTexture(0, input.getColorTextureId());
        PbrRenderer.setSmoothAlpha(alpha);
        RenderSystem.setShader(() -> PbrRenderer.smoothShader);
        quad.bind();
        quad.drawWithShader(IDENTITY, IDENTITY, PbrRenderer.smoothShader);
    }

    private static void compositeToMain(RenderTarget narrow, RenderTarget wide, ShaderInstance shader,
                                        int maskTexture, float strength, float wideStrength) {
        // Apply the shader first, then establish the actual draw state. ShaderInstance
        // and third-party hooks may otherwise replace factors set before apply().
        shader.setSampler("Sampler0", narrow.getColorTextureId());
        shader.setSampler("Wide", wide.getColorTextureId());
        shader.setSampler("ScopeMask", maskTexture);
        PbrRenderer.setBloomStrength(strength);
        PbrRenderer.setWideBloomStrength(wideStrength);
        PbrRenderer.setHasScopeMask(maskTexture != 0 ? 1 : 0);
        quad.bind();
        shader.apply();
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        boolean scissored = applyPassScissor(mainTarget);
        PbrRenderer.setCompositeScissor(scissored, SCISSOR_RECT[0], SCISSOR_RECT[1],
                SCISSOR_RECT[0] + SCISSOR_RECT[2], SCISSOR_RECT[1] + SCISSOR_RECT[3],
                Math.max(16.0F, Math.min(scissorMarginPixels, 0.45F * Math.min(SCISSOR_RECT[2], SCISSOR_RECT[3]))));
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

    /** 可分离高斯的一遍。 */
    private static void drawBlurPass(RenderTarget input, RenderTarget output, float directionX, float directionY) {
        PbrRenderer.setBlurDirection(directionX, directionY);
        draw(input, output, PbrRenderer.blurShader);
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
        detachFromMainTarget();
        if (fallbackFbo >= 0) {
            GL30.glDeleteFramebuffers(fallbackFbo);
            fallbackFbo = -1;
        }
        if (emission != null) emission.destroyBuffers();
        if (emissionDepth != null) emissionDepth.destroyBuffers();
        if (masked != null) masked.destroyBuffers();
        if (narrowA != null) narrowA.destroyBuffers();
        if (narrowB != null) narrowB.destroyBuffers();
        if (wideA != null) wideA.destroyBuffers();
        if (wideB != null) wideB.destroyBuffers();
        if (smoothNarrow != null) smoothNarrow.destroyBuffers();
        if (smoothWide != null) smoothWide.destroyBuffers();
        emission = emissionDepth = masked = narrowA = narrowB = wideA = wideB = null;
        smoothNarrow = smoothWide = null;
        smoothValid = false;
        attachedFrameBufferId = -1;
        releaseScopeMask();
        dirty = false;
    }

    /** 销毁发光贴图前先从主目标上摘掉, 避免主 FBO 因为附件被删除而变成不完整。 */
    private static void detachFromMainTarget() {
        if (attachedFrameBufferId < 0) {
            return;
        }
        try {
            int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, attachedFrameBufferId);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        } catch (Throwable ignored) {
        }
        attachedFrameBufferId = -1;
    }

    public static void release() {
        releaseTargets();
        chainValid = false;
        if (quad != null) { quad.close(); quad = null; }
        if (maskQuad != null) { maskQuad.close(); maskQuad = null; }
        mrtUnavailable = false;
        fallbackUnavailable = false;
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

    public static void captureScopeMask() {
        // 泛光链路本来就不跑时没必要为它做一次全屏 stencil 拷贝。
        if (!configured() || !pureScopeAiming()) {
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
