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
import com.ssscript.taczfixes.client.util.ScopeSwitchState;
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.util.AimingBlurConfig;
import com.ssscript.taczfixes.common.util.AimingBlurDisplayAccessor;
import com.ssscript.taczfixes.common.util.CustomSlotStorage;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;
import java.util.Optional;

/**
 * 开镜模糊: 在 renderLevel 结束后对主帧缓冲做高斯模糊, 只作用于屏幕边缘。
 * 模糊分两层叠加(弱模糊 + 在其之上的强模糊), 通过随边缘距离渐增的遮罩混合,
 * 使靠近中心的模糊较弱、靠边缘的模糊较强; 强度按开镜进度渐入渐出。
 * 模糊范围随瞄准方块的距离变化: 远处最大, 近处趋近于无。
 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AimBlur {
    private static ShaderInstance shader;
    private static com.mojang.blaze3d.shaders.AbstractUniform uMaskStart;
    private static RenderTarget horizontal, vertical;
    private static VertexBuffer quad;
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** 视线检测的最远距离(格)。 */
    private static final double RAY_DISTANCE = 128.0;
    /** 瞄准距离小于等于该值时模糊范围衰减为 0。 */
    private static final double RANGE_NEAR_DISTANCE = 2.0;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        release();
        shader = null;
        uMaskStart = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "aim_blur"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    shader = s;
                    uMaskStart = s.safeGetUniform("MaskStart");
                });
    }

    /** 在 renderLevel 结束时调用: 模糊已绘制完的世界与手部, 不影响之后绘制的 HUD。 */
    public static void composite() {
        if (shader == null || PbrRenderer.blurShader == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator operator)) return;
        AimingParams params = resolveParams(player);
        if (!params.enabled()) return;
        float progress = operator.getClientAimingProgress(mc.getFrameTime());
        if (progress <= 0.01f) return;
        float range = params.range() * distanceFactor(mc, player, params);
        float radius = params.strength() * progress;
        if (range <= 0.002f || radius < 0.1f) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        ensureTargets(main);
        if (horizontal == null || vertical == null) return;

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
            // 第一层: 弱模糊(半径 = 总强度的一半), 覆盖范围带的内侧
            float halfRadius = radius * 0.5f;
            PbrRenderer.setBlurDirection(halfRadius / main.width, 0f);
            draw(horizontal, main.getColorTextureId(), PbrRenderer.blurShader);
            PbrRenderer.setBlurDirection(0f, halfRadius / main.height);
            draw(vertical, horizontal.getColorTextureId(), PbrRenderer.blurShader);
            compositeMasked(main, vertical, 1.0f - range);
            // 第二层: 在第一层结果上继续模糊(累计半径 ≈ 总强度), 覆盖范围带的外侧
            float addRadius = radius * 0.866f;
            PbrRenderer.setBlurDirection(addRadius / main.width, 0f);
            draw(horizontal, vertical.getColorTextureId(), PbrRenderer.blurShader);
            PbrRenderer.setBlurDirection(0f, addRadius / main.height);
            draw(vertical, horizontal.getColorTextureId(), PbrRenderer.blurShader);
            compositeMasked(main, vertical, 1.0f - range * 0.5f);
        } finally {
            VertexBuffer.unbind();
            main.bindWrite(true);
            for (int i = 0; i < textures.length; i++) RenderSystem.setShaderTexture(i, textures[i]);
            RenderSystem.setShader(() -> previous);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            PbrBlendCache.restore();
            RenderSystem.depthMask(depthMask);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST);
        }
    }

    /** 开镜模糊参数: 瞄具 display 的 aiming_blur 优先, 未配置项回退配置文件。 */
    private record AimingParams(boolean enabled, float range, float strength, double farDistance, float minFactor) {
    }

    private static AimingParams resolveParams(LocalPlayer player) {
        AimingBlurConfig cfg = resolveDisplayConfig(player);
        boolean enabled = cfg != null && cfg.aim_blur_enabled != null
                ? cfg.aim_blur_enabled.booleanValue()
                : Config.AIM_BLUR_ENABLED.get();
        float range = cfg != null && cfg.aim_blur_range != null
                ? cfg.aim_blur_range.floatValue()
                : Config.AIM_BLUR_RANGE.get().floatValue();
        float strength = cfg != null && cfg.aim_blur_strength != null
                ? cfg.aim_blur_strength.floatValue()
                : Config.AIM_BLUR_STRENGTH.get().floatValue();
        double far = cfg != null && cfg.aim_blur_far_distance != null
                ? cfg.aim_blur_far_distance.doubleValue()
                : Config.AIM_BLUR_FAR_DISTANCE.get();
        float min = cfg != null && cfg.aim_blur_min_factor != null
                ? cfg.aim_blur_min_factor.floatValue()
                : Config.AIM_BLUR_MIN_FACTOR.get().floatValue();
        return new AimingParams(enabled, range, strength, far, min);
    }

    /** 当前生效瞄具(或切换到的自定义槽瞄具)的 display 上的 aiming_blur 配置, 无则 null。 */
    private static AimingBlurConfig resolveDisplayConfig(LocalPlayer player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return null;

        ResourceLocation slotId = null;
        String active = ScopeSwitchState.getActiveSlot(gunStack);
        if (active != null) {
            ItemStack scope = CustomSlotStorage.get(gunStack, active);
            if (!scope.isEmpty()) {
                IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
                if (attachment != null) {
                    slotId = attachment.getAttachmentId(scope);
                }
            }
        }
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) {
            slotId = gun.getAttachmentId(gunStack, AttachmentType.SCOPE);
        }
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) {
            slotId = gun.getBuiltInAttachmentId(gunStack, AttachmentType.SCOPE);
        }
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) return null;

        Optional<CommonAttachmentIndex> indexOpt = TimelessAPI.getCommonAttachmentIndex(slotId);
        if (indexOpt.isEmpty()) return null;
        ResourceLocation displayId = indexOpt.get().getPojo().getDisplay();
        if (displayId == null) return null;
        AttachmentDisplay display = ClientAssetsManager.INSTANCE.getAttachmentDisplay(displayId);
        if (!(display instanceof AimingBlurDisplayAccessor accessor)) return null;
        return accessor.getAimingBlur();
    }

    /** 模糊范围随瞄准目标(方块/实体)距离的系数: 0距离处为配置的最小比例, 远处为 1; 未命中(天空等)按最远处处理。 */
    private static float distanceFactor(Minecraft mc, LocalPlayer player, AimingParams params) {
        float partial = mc.getFrameTime();
        Vec3 eye = player.getEyePosition(partial);
        Vec3 view = player.getViewVector(partial);
        Vec3 end = eye.add(view.scale(RAY_DISTANCE));
        double distance = RAY_DISTANCE;
        HitResult blockHit = player.pick(RAY_DISTANCE, partial, false);
        if (blockHit.getType() != HitResult.Type.MISS) {
            distance = Math.min(distance, eye.distanceTo(blockHit.getLocation()));
        }
        AABB area = player.getBoundingBox().expandTowards(view.scale(RAY_DISTANCE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                entity -> !entity.isSpectator() && entity.isPickable(), RAY_DISTANCE * RAY_DISTANCE);
        if (entityHit != null) {
            distance = Math.min(distance, eye.distanceTo(entityHit.getLocation()));
        }
        double far = Math.max(RANGE_NEAR_DISTANCE + 0.001, params.farDistance());
        float factor = Mth.clamp(
                (float) ((distance - RANGE_NEAR_DISTANCE) / (far - RANGE_NEAR_DISTANCE)),
                0.0f, 1.0f);
        // 非线性: 近距离即有明显模糊, 随距离快速上升后逐渐饱和(sqrt 曲线)
        float min = params.minFactor();
        return min + (1.0f - min) * (float) Math.sqrt(factor);
    }

    private static void ensureTargets(RenderTarget main) {
        if (horizontal != null && horizontal.width == main.width && horizontal.height == main.height) {
            return;
        }
        releaseTargets();
        horizontal = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        vertical = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        horizontal.setFilterMode(GL11.GL_LINEAR);
        vertical.setFilterMode(GL11.GL_LINEAR);
    }

    private static void draw(RenderTarget output, int inputTexture, ShaderInstance shader) {
        output.bindWrite(true);
        RenderSystem.setShaderTexture(0, inputTexture);
        RenderSystem.setShader(() -> shader);
        quad.bind();
        quad.drawWithShader(IDENTITY, IDENTITY, shader);
    }

    /** 将模糊结果按边缘遮罩叠加到主帧缓冲: MaskStart 处开始出现该层模糊。 */
    private static void compositeMasked(RenderTarget main, RenderTarget input, float maskStart) {
        if (uMaskStart != null) uMaskStart.set(maskStart);
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        main.bindWrite(true);
        compositeToMain(input, shader);
    }

    private static void compositeToMain(RenderTarget input, ShaderInstance shader) {
        shader.setSampler("Sampler0", input.getColorTextureId());
        quad.bind();
        shader.apply();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            quad.draw();
        } finally {
            shader.clear();
        }
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
        if (horizontal != null) horizontal.destroyBuffers();
        if (vertical != null) vertical.destroyBuffers();
        horizontal = vertical = null;
    }

    public static void release() {
        releaseTargets();
        if (quad != null) { quad.close(); quad = null; }
    }

    private AimBlur() {
    }
}
