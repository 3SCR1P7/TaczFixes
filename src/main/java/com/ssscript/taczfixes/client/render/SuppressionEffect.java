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
import com.ssscript.taczfixes.common.config.Config;
import com.ssscript.taczfixes.common.data.GunTaczFixesData;
import com.ssscript.taczfixes.common.data.TaczFixesDataManager;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 压制效果: 非自身发射的子弹从玩家附近飞过时, 屏幕边缘施加高斯模糊(越靠中心越弱, 复用开镜模糊的做法),
 * 并叠加径向色散与边缘变暗; 三者在子弹飞过的瞬间达到最大值后线性淡出。
 * sniper/rpg 类型武器的子弹持续时长与检测范围按配置倍率放大; 单把枪可在 taczfixes.suppression 中逐项覆盖。
 */
@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SuppressionEffect {
    private static ShaderInstance shader;
    private static com.mojang.blaze3d.shaders.AbstractUniform uMaskStart, uMaskStartWeak, uBlurOpacity;
    private static com.mojang.blaze3d.shaders.AbstractUniform uAbOpacity, uDarken, uDarkenMaskStart, uDarkenOpacity, uAberration;
    private static RenderTarget original, weakH, weakV, strongH, strongV;
    private static VertexBuffer quad;
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static double blurRemainingMs, blurTotalMs, abRemainingMs, abTotalMs;
    private static double darkenRemainingMs, darkenTotalMs;
    /** 本次激活生效的强度/范围(来自全局配置或被枪械 data 覆盖), 在包络重启时更新。 */
    private static float activeBlurStrength, activeBlurRange, activeAberration;
    private static float activeDarkenStrength, activeDarkenRange;
    /** 上一帧的真实时间(nanos), 用于按毫秒推进淡出。 */
    private static long lastFrameNanos;
    /** 生成瞬间(spawn 数据未就绪)已进入检测范围、等待按真实 gunId 判定的子弹。
     *  此刻实体 id 尚未赋值(ClientboundAddEntity 先设位置后 setId), 因此按对象身份跟踪。 */
    private static final Set<EntityKineticBullet> pendingBullets =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /** 已触发过压制效果的子弹 id: 每颗子弹只触发一次。 */
    private static final Set<Integer> triggeredBullets = new HashSet<>();
    private static final int TRIGGERED_LIMIT = 4096;
    private static final int PENDING_LIMIT = 128;

    /** 单次触发使用的完整参数: 全局配置 + 枪械 data(taczfixes.suppression)逐项覆盖。 */
    private record Params(boolean enabled, double detectionRange,
                          double exposureStrength, double exposureDuration,
                          double blurStrength, double blurDuration, double blurRange,
                          double darkenStrength, double darkenDuration, double darkenRange) {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        release();
        shader = null;
        uMaskStart = uMaskStartWeak = uBlurOpacity = null;
        uAbOpacity = uDarken = uDarkenMaskStart = uDarkenOpacity = uAberration = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "suppress_blur"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    shader = s;
                    uMaskStart = s.safeGetUniform("MaskStart");
                    uMaskStartWeak = s.safeGetUniform("MaskStartWeak");
                    uBlurOpacity = s.safeGetUniform("BlurOpacity");
                    uAbOpacity = s.safeGetUniform("AbOpacity");
                    uDarken = s.safeGetUniform("Darken");
                    uDarkenMaskStart = s.safeGetUniform("DarkenMaskStart");
                    uDarkenOpacity = s.safeGetUniform("DarkenOpacity");
                    uAberration = s.safeGetUniform("Aberration");
                });
    }

    /**
     * 由 Entity.m_20343_ 注入调用(仅对子弹实体): 位置变化时对“上一位置 → 新位置”线段做扫掠检测。
     * 客户端子弹自身不移动(onBulletTick 仅服务端执行), 位置完全由同步包驱动。
     */
    public static void onBulletPositionSet(EntityKineticBullet bullet, double x, double y, double z) {
        if (!bullet.level().isClientSide()) return;
        Vec3 from = bullet.position();
        Vec3 to = new Vec3(x, y, z);
        if (from.distanceToSqr(to) < 1.0e-8) return;
        if (!Config.SUPPRESSION_ENABLED.get()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || bullet.level() != mc.level || mc.isPaused()) return;

        // 数据上限: 每颗子弹只触发一次; 挂起中的子弹不重复处理
        if (pendingBullets.contains(bullet)) return;
        int id = bullet.getId();
        if (id != 0 && triggeredBullets.contains(id)) return;
        if (triggeredBullets.size() > TRIGGERED_LIMIT) triggeredBullets.clear();
        if (pendingBullets.size() > PENDING_LIMIT) pendingBullets.clear();

        ResourceLocation gunId = bullet.getGunId();
        if (gunId == null) {
            // 生成瞬间的位置同步早于 spawn 数据(gunId/owner): 先按对象挂起,
            // 等 readSpawnData 结束(数据就绪的准确时刻)再用真实 gunId 判定并触发。
            double baseRange = Config.SUPPRESSION_DETECTION_RANGE.get();
            if (distanceToSegmentSqr(player.getEyePosition(1.0f), from, to) > baseRange * baseRange) return;
            pendingBullets.add(bullet);
            return;
        }
        Params params = resolveParams(gunId);
        if (!params.enabled()) {
            if (id != 0) triggeredBullets.add(id);
            return;
        }
        Entity owner = bullet.getOwner();
        if (!Config.SUPPRESSION_INCLUDE_OWN_BULLETS.get() && (owner == null || owner == player)) return;
        boolean heavy = isHeavy(gunId);
        double range = params.detectionRange()
                * (heavy ? Config.SUPPRESSION_SNIPER_RPG_RANGE_MULTIPLIER.get() : 1.0);
        if (range <= 0.0) return;
        double distSqr = distanceToSegmentSqr(player.getEyePosition(1.0f), from, to);
        if (distSqr > range * range) return;
        if (id != 0 && triggeredBullets.add(id)) {
            trigger(heavy, params);
        }
    }

    /** readSpawnData 完成(枪械数据已就绪)时由 mixin 调用: 处理生成瞬间已进入检测范围的挂起子弹。 */
    public static void onBulletDataReady(EntityKineticBullet bullet) {
        if (!bullet.level().isClientSide()) return;
        if (!pendingBullets.remove(bullet)) return;
        int id = bullet.getId();
        if (id != 0) triggeredBullets.add(id);
        ResourceLocation gunId = bullet.getGunId();
        if (gunId == null) return;
        Params params = resolveParams(gunId);
        if (!params.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            Entity owner = bullet.getOwner();
            if (!Config.SUPPRESSION_INCLUDE_OWN_BULLETS.get() && (owner == null || owner == player)) return;
        }
        trigger(isHeavy(gunId), params);
    }

    private static boolean isHeavy(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    String type = index.getType();
                    return "sniper".equals(type) || "rpg".equals(type);
                })
                .orElse(false);
    }

    private static Params resolveParams(ResourceLocation gunId) {
        GunTaczFixesData.SuppressionConfig cfg = null;
        if (gunId != null) {
            cfg = TaczFixesDataManager.getSuppressionConfig(TaczFixesDataManager.resolveDataId(gunId));
        }
        boolean enabled = cfg == null || cfg.enable == null || cfg.enable;
        GunTaczFixesData.SuppressionExposureConfig ex = cfg == null ? null : cfg.exposure;
        GunTaczFixesData.SuppressionBlurConfig bl = cfg == null ? null : cfg.blur;
        GunTaczFixesData.SuppressionDarkenConfig dk = cfg == null ? null : cfg.darken;
        return new Params(
                enabled,
                pick(cfg == null ? null : cfg.detection_range, Config.SUPPRESSION_DETECTION_RANGE.get()),
                pick(ex == null ? null : ex.strength, Config.SUPPRESSION_EXPOSURE_STRENGTH.get()),
                pickInt(ex == null ? null : ex.duration, Config.SUPPRESSION_EXPOSURE_DURATION_MS.get()),
                pick(bl == null ? null : bl.strength, Config.SUPPRESSION_BLUR_STRENGTH.get()),
                pickInt(bl == null ? null : bl.duration, Config.SUPPRESSION_BLUR_DURATION_MS.get()),
                pick(bl == null ? null : bl.range, Config.SUPPRESSION_BLUR_RANGE.get()),
                pick(dk == null ? null : dk.strength, Config.SUPPRESSION_DARKEN_STRENGTH.get()),
                pickInt(dk == null ? null : dk.duration, Config.SUPPRESSION_DARKEN_DURATION_MS.get()),
                pick(dk == null ? null : dk.range, Config.SUPPRESSION_DARKEN_RANGE.get()));
    }

    private static double pick(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static double pickInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /** 点到线段的最短距离的平方(子弹观测位置之间的扫掠路径)。 */
    private static double distanceToSegmentSqr(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double lenSqr = dir.lengthSqr();
        if (lenSqr < 1.0e-6) return point.distanceToSqr(to);
        double t = Mth.clamp(point.subtract(from).dot(dir) / lenSqr, 0.0, 1.0);
        return point.distanceToSqr(from.add(dir.scale(t)));
    }

    private static void trigger(boolean heavy, Params params) {
        boolean idle = blurRemainingMs <= 0.0 && abRemainingMs <= 0.0 && darkenRemainingMs <= 0.0;
        double exposure = params.exposureDuration();
        double blur = params.blurDuration();
        double darken = params.darkenDuration();
        double blurStrength = params.blurStrength();
        double abStrength = params.exposureStrength();
        double darkenStrength = params.darkenStrength();
        if (heavy) {
            exposure *= Config.SUPPRESSION_SNIPER_RPG_EXPOSURE_MULTIPLIER.get();
            blur *= Config.SUPPRESSION_SNIPER_RPG_BLUR_MULTIPLIER.get();
            darken *= Config.SUPPRESSION_SNIPER_RPG_DARKEN_DURATION_MULTIPLIER.get();
            blurStrength *= Config.SUPPRESSION_SNIPER_RPG_BLUR_STRENGTH_MULTIPLIER.get();
            abStrength *= Config.SUPPRESSION_SNIPER_RPG_EXPOSURE_STRENGTH_MULTIPLIER.get();
            darkenStrength *= Config.SUPPRESSION_SNIPER_RPG_DARKEN_STRENGTH_MULTIPLIER.get();
        }
        if (idle) {
            activeBlurStrength = (float) blurStrength;
            activeBlurRange = (float) params.blurRange();
            activeAberration = (float) abStrength;
            activeDarkenStrength = (float) darkenStrength;
            activeDarkenRange = (float) params.darkenRange();
        }
        // 新触发刷新对应包络时同时更新该效果的强度/范围, 保证与本次触发的数据一致。
        if (blur >= blurRemainingMs) {
            blurRemainingMs = blur;
            blurTotalMs = blur;
            activeBlurStrength = (float) blurStrength;
            activeBlurRange = (float) params.blurRange();
        }
        if (exposure >= abRemainingMs) {
            abRemainingMs = exposure;
            abTotalMs = exposure;
            activeAberration = (float) abStrength;
        }
        if (darken >= darkenRemainingMs) {
            darkenRemainingMs = darken;
            darkenTotalMs = darken;
            activeDarkenStrength = (float) darkenStrength;
            activeDarkenRange = (float) params.darkenRange();
        }
    }

    /** 在 renderLevel 结束时调用: 作用于世界与手部, 不影响之后绘制的 HUD。 */
    public static void composite() {
        Minecraft mc = Minecraft.getInstance();
        // 用真实时间推进: getDeltaFrameTime() 的单位是 tick 而不是毫秒, 不能直接当毫秒用。
        long now = System.nanoTime();
        double deltaMs = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) / 1.0e6;
        lastFrameNanos = now;
        if (deltaMs < 0.0) deltaMs = 0.0;
        if (deltaMs > 100.0) deltaMs = 100.0;
        blurRemainingMs = Math.max(0.0, blurRemainingMs - deltaMs);
        abRemainingMs = Math.max(0.0, abRemainingMs - deltaMs);
        darkenRemainingMs = Math.max(0.0, darkenRemainingMs - deltaMs);
        if (blurRemainingMs <= 0.0 && abRemainingMs <= 0.0 && darkenRemainingMs <= 0.0) return;
        if (!Config.SUPPRESSION_ENABLED.get()) return;
        if (shader == null || PbrRenderer.blurShader == null || mc.player == null) return;
        float blurOpacity = blurTotalMs > 0.0 ? (float) (blurRemainingMs / blurTotalMs) : 0.0f;
        float abOpacity = abTotalMs > 0.0 ? (float) (abRemainingMs / abTotalMs) : 0.0f;
        float darkenOpacity = darkenTotalMs > 0.0 ? (float) (darkenRemainingMs / darkenTotalMs) : 0.0f;
        float radius = activeBlurStrength;
        float range = activeBlurRange;
        float aberration = activeAberration;
        float darkenStrength = activeDarkenStrength;
        float darkenRange = activeDarkenRange;
        boolean blurActive = blurOpacity > 0.001f && radius >= 0.1f && range > 0.002f;
        boolean darkenActive = darkenOpacity > 0.001f && darkenStrength > 0.001f && darkenRange > 0.002f;
        if (!blurActive && abOpacity <= 0.001f && !darkenActive) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        ensureTargets(main);
        if (original == null) return;

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
            // 原画面拷贝(供色散采样, 避免自读自写)
            PbrRenderer.setBlurDirection(0f, 0f);
            draw(original, main.getColorTextureId(), PbrRenderer.blurShader);
            if (blurActive) {
                // 弱模糊层
                float weakRadius = radius * 0.5f;
                PbrRenderer.setBlurDirection(weakRadius / main.width, 0f);
                draw(weakH, original.getColorTextureId(), PbrRenderer.blurShader);
                PbrRenderer.setBlurDirection(0f, weakRadius / main.height);
                draw(weakV, weakH.getColorTextureId(), PbrRenderer.blurShader);
                // 强模糊层(在弱模糊基础上继续模糊, 累计半径 ≈ 设定强度)
                float addRadius = radius * 0.866f;
                PbrRenderer.setBlurDirection(addRadius / main.width, 0f);
                draw(strongH, weakV.getColorTextureId(), PbrRenderer.blurShader);
                PbrRenderer.setBlurDirection(0f, addRadius / main.height);
                draw(strongV, strongH.getColorTextureId(), PbrRenderer.blurShader);
            }
            PbrRenderer.setUniform(uMaskStart, 1.0f - range * 0.5f);
            PbrRenderer.setUniform(uMaskStartWeak, 1.0f - range);
            PbrRenderer.setUniform(uBlurOpacity, blurActive ? blurOpacity : 0f);
            PbrRenderer.setUniform(uAbOpacity, abOpacity);
            PbrRenderer.setUniform(uDarken, darkenStrength);
            PbrRenderer.setUniform(uDarkenMaskStart, 1.0f - darkenRange);
            PbrRenderer.setUniform(uDarkenOpacity, darkenActive ? darkenOpacity : 0f);
            float ab = aberration / Math.max(1, main.height);
            PbrRenderer.setUniform(uAberration, ab, ab);
            shader.setSampler("Sampler0", weakV.getColorTextureId());
            shader.setSampler("Sampler1", strongV.getColorTextureId());
            shader.setSampler("Sampler2", original.getColorTextureId());
            main.bindWrite(true);
            quad.bind();
            shader.apply();
            try {
                quad.draw();
            } finally {
                shader.clear();
            }
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
            if (blurRemainingMs <= 0.0) blurTotalMs = 0.0;
            if (abRemainingMs <= 0.0) abTotalMs = 0.0;
            if (darkenRemainingMs <= 0.0) darkenTotalMs = 0.0;
        }
    }

    private static void ensureTargets(RenderTarget main) {
        int blurWidth = Math.max(1, main.width / 2);
        int blurHeight = Math.max(1, main.height / 2);
        if (original != null && original.width == main.width && original.height == main.height
                && weakH != null && weakH.width == blurWidth && weakH.height == blurHeight) {
            return;
        }
        releaseTargets();
        // 原画面保持全分辨率(色散需要原始细节); 模糊层降半分辨率, 填充率降到 1/4。
        // 模糊方向是 UV 单位, 用 main 尺寸计算可保持相同的屏幕空间模糊半径。
        original = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        weakH = new TextureTarget(blurWidth, blurHeight, false, Minecraft.ON_OSX);
        weakV = new TextureTarget(blurWidth, blurHeight, false, Minecraft.ON_OSX);
        strongH = new TextureTarget(blurWidth, blurHeight, false, Minecraft.ON_OSX);
        strongV = new TextureTarget(blurWidth, blurHeight, false, Minecraft.ON_OSX);
        for (RenderTarget target : new RenderTarget[]{original, weakH, weakV, strongH, strongV}) {
            target.setFilterMode(GL11.GL_LINEAR);
        }
    }

    private static void draw(RenderTarget output, int inputTexture, ShaderInstance shader) {
        output.bindWrite(true);
        RenderSystem.setShaderTexture(0, inputTexture);
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
        if (original != null) original.destroyBuffers();
        if (weakH != null) weakH.destroyBuffers();
        if (weakV != null) weakV.destroyBuffers();
        if (strongH != null) strongH.destroyBuffers();
        if (strongV != null) strongV.destroyBuffers();
        original = weakH = weakV = strongH = strongV = null;
    }

    public static void release() {
        releaseTargets();
        if (quad != null) {
            quad.close();
            quad = null;
        }
    }

    private SuppressionEffect() {
    }
}
