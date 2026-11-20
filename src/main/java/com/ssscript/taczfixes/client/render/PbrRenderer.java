package com.ssscript.taczfixes.client.render;
import com.ssscript.taczfixes.client.mixin.shader.PbrCompositeAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrStateAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrTextureAccessor;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import com.ssscript.taczfixes.TaczFixesMod;

import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.CloudStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader;
    private static AbstractUniform uHasSpecular, uHasNormal, uEmissionPass, uScopeDivisionPass;
    private static AbstractUniform uReflectionStrength, uGlareStrength, uEmissionStrength;
    private static AbstractUniform uGlareDepthTest, uFallbackMaterial;
    private static final int LOCAL_LIGHT_SLOTS = 4;
    private static final AbstractUniform[] uLocalLightPos = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static final AbstractUniform[] uLocalLightColor = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static final AbstractUniform[] uLocalLightStrength = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static AbstractUniform uCelestialDirection, uCelestialColor, uSkyAmbient;
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness, uWetSurface;
    private static AbstractUniform uSkyZenith, uSkyHorizon, uSunHorizon, uSunAzimuth, uSunOcclusion;
    private static AbstractUniform uDefaultRoughness, uDefaultMetal, uDefaultReflection, uEnvStrength;
    private static AbstractUniform uAnisotropy, uWetDetail, uSSRStrength, uTime;
    private static AbstractUniform uViewRotX, uViewRotY, uViewRotZ;
    private static AbstractUniform uCameraPos;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uWideStrength, uHasScopeMask;
    private static AbstractUniform uCamoIndex, uCamoStrength, uCamoEmissive;
    private static VertexBuffer mesh;
    private static int emissionBlendOverride;
    private static boolean acceleratedPbrPass;
    private static boolean emissionReplay;
    private static final int MATERIALS_MAX = 256;

    /** 不同迷彩的枪共用同一张贴图, 但必须分开成不同 RenderType, 才能各带各的 uniform。 */
    private static final int CAMO_TYPES_MAX = 256;
    private static final Map<CamoKey, RenderType> CAMO_TYPES =
            new java.util.LinkedHashMap<>(CAMO_TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CamoKey, RenderType> eldest) {
                    return size() > CAMO_TYPES_MAX;
                }
            };
    private static volatile ShaderInstance camoShader;

    private record CamoKey(RenderType original, int index, float strength, float emissive,
                           boolean hand, boolean rainExposed) {
    }

    /** 同一 RenderType 按"手持物/世界物体"与"该物体是否被雨淋到"拆成变体, 湿润量来源不同。 */
    private record TypeKey(RenderType original, boolean hand, boolean rainExposed) {
    }

    private record CamoUniforms(int index, float strength, float emissive) {
        static final CamoUniforms NONE = new CamoUniforms(0, 1.0f, 0.0f);
    }

    /** 镜片/分划之类"镜内可见"的部件: 渲染期间禁用动态迷彩(瞄具内部阶段)。 */
    private static int camoSuppression;

    public static void pushCamoSuppression() {
        camoSuppression++;
    }

    public static void popCamoSuppression() {
        if (camoSuppression > 0) camoSuppression--;
    }

    private static int sceneColorTexture;
    private static int sceneDepthTexture;
    private static int sceneColorWidth, sceneColorHeight;
    private static int sceneDepthWidth, sceneDepthHeight;
    private static boolean sceneColorCaptured;

    public static void beginAcceleratedPass() {
        acceleratedPbrPass = true;
    }

    public static void endAcceleratedPass() {
        acceleratedPbrPass = false;
    }

    static boolean isAcceleratedPbrPass() {
        return acceleratedPbrPass;
    }

    public static boolean isEmissionReplay() {
        return emissionReplay;
    }

    public static boolean shouldReplayEmission(RenderType type) {
        // 加速渲染跳过了主绘制, 需要补画一次发光通道; 是否真的画由 draw() 里
        // canCapture()+hasEmissionSource() 决定(发光骨骼在补画时才会打上标记)。
        return !isEmissionReplay() && !isAcceleratedPbrPass() && PbrBloom.configured()
                && com.tacz.guns.compat.ar.ARCompat.shouldAccelerate()
                && resolve(type) instanceof PbrType
                && (!isTracerBloomSuppressed() || Config.PBR_BLOOM_TRACER.get());
    }

    public static void beginEmissionReplay() {
        emissionReplay = true;
    }

    public static void endEmissionReplay() {
        emissionReplay = false;
    }

    public static void applyEmissionBlendOverride() {
        if (emissionBlendOverride > 0) {
            RenderSystem.disableBlend();
        }
    }

    private static void pushEmissionBlendOverride() {
        emissionBlendOverride++;
    }

    private static void popEmissionBlendOverride() {
        if (emissionBlendOverride > 0) {
            emissionBlendOverride--;
        }
    }
    private static final Map<ResourceLocation, Optional<Material>> MATERIALS =
            new java.util.LinkedHashMap<>(MATERIALS_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Optional<Material>> eldest) {
                    return size() > MATERIALS_MAX;
                }
            };
    private static final int TYPES_MAX = 512;
    private static final Map<TypeKey, RenderType> TYPES =
            new java.util.LinkedHashMap<>(TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<TypeKey, RenderType> eldest) {
                    return size() > TYPES_MAX;
                }
            };
    private static final Method OPTIFINE_SHADERS = findOptifine();

    private record Material(ResourceLocation specular, ResourceLocation normal, boolean emissive) {}

    /**
     * 本帧是否出现过真正的自发光来源: 自发光骨骼({@code *_illuminated})、自发光贴图(_s alpha)、
     * 镭射光束、曳光弹、发光迷彩。泛光链路只在这类内容存在时才跑, 没有自发光时完全跳过。
     */
    private static boolean frameEmission;

    /** 每帧开头清空自发光来源标记(由 {@link PbrBloom#beginFrame()} 调用)。 */
    public static void beginFrame() {
        frameEmission = false;
    }

    /** 记录一个自发光来源。 */
    public static void markEmissionSource() {
        frameEmission = true;
    }

    /** 本帧是否有自发光内容可泛。 */
    public static boolean hasEmissionSource() {
        return frameEmission;
    }

    private static int tracerBloomSuppression;
    private static int materialFallbackSuppression;

    public static void pushMaterialFallbackSuppression() {
        materialFallbackSuppression++;
    }

    public static void popMaterialFallbackSuppression() {
        if (materialFallbackSuppression > 0) {
            materialFallbackSuppression--;
        }
    }

    public static void pushTracerBloomSuppression() {
        tracerBloomSuppression++;
    }

    public static void popTracerBloomSuppression() {
        if (tracerBloomSuppression > 0) {
            tracerBloomSuppression--;
        }
    }

    public static boolean isTracerBloomSuppressed() {
        return tracerBloomSuppression > 0;
    }

    private static final java.util.ArrayDeque<Boolean> DIVISION_RENDER_STACK = new java.util.ArrayDeque<>();

    public static void pushScopeDivision(boolean division) {
        DIVISION_RENDER_STACK.push(division);
    }

    public static void popScopeDivision() {
        DIVISION_RENDER_STACK.poll();
    }

    public static boolean isScopeDivision() {
        return Boolean.TRUE.equals(DIVISION_RENDER_STACK.peek());
    }

    public static void resetScopeDivision() {
        DIVISION_RENDER_STACK.clear();
        scopeAttachmentDepth = 0;
    }

    private static int scopeAttachmentDepth;

    public static void pushScopeAttachment() {
        scopeAttachmentDepth++;
    }

    public static void popScopeAttachment() {
        if (scopeAttachmentDepth > 0) {
            scopeAttachmentDepth--;
        }
    }

    public static boolean isRenderingScopeAttachment() {
        return scopeAttachmentDepth > 0;
    }

    public static void updateCelestialLight(float partialTick) {
        // 光影启用时 PBR 已被强制关闭, 不再更新 PBR uniform
        sceneColorCaptured = false;
        if (!enabled()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var origin = camera.getPosition();
        // 视线在水下(相机位于水中)时, 手持枪械也应算作被水浸湿。
        boolean underwater = camera.getFluidInCamera() == FogType.WATER;
        // 世界->视角空间的旋转必须来自相机本身。手持枪械的 ModelViewMat 还包含枪械
        // 模型自身的变换, 直接取它的旋转会让太阳/火把方向整体偏转, 于是斜对光源的
        // 面在正确角度上却看不到反光。
        var look = camera.getLookVector();
        var up = camera.getUpVector();
        var left = camera.getLeftVector();
        setUniform(uViewRotX, -left.x(), up.x(), -look.x());
        setUniform(uViewRotY, -left.y(), up.y(), -look.y());
        setUniform(uViewRotZ, -left.z(), up.z(), -look.z());
        setUniform(uCameraPos, (float) origin.x, (float) origin.y, (float) origin.z);
        float dt = frameDelta();
        if (System.currentTimeMillis() - lastPbrDrawAt <= 500L) {
            updateLocalLights(level, origin, dt);
        } else {
            decayLocalLights(dt);
        }
        applyLocalLightUniforms();
        setUniform(uTime, (level.getGameTime() + partialTick) / 20f);
        setUniform(uEnvStrength, Config.PBR_ENV_STRENGTH.get().floatValue());
        setUniform(uAnisotropy, Config.PBR_ANISOTROPY.get().floatValue());
        if (!level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            setUniform(uCelestialDirection, 0f, 1f, 0f);
            setUniform(uCelestialColor, 0f, 0f, 0f);
            setUniform(uSkyAmbient, 0f);
            setUniform(uCelestialVisibility, updateCelestialVisibility(0f, dt));
            setUniform(uLocalSkyExposure, 0f);
            updateWetnessState(underwater, false, 0f, false);
            setUniform(uSkyZenith, 0.02f, 0.02f, 0.03f);
            setUniform(uSkyHorizon, 0.04f, 0.03f, 0.03f);
            setUniform(uSunHorizon, 0f, 0f, 0f);
            setUniform(uSunAzimuth, 1f, 0f);
            setUniform(uSunOcclusion, 0f);
            return;
        }
        float timeOfDay = level.getTimeOfDay(partialTick);
        var light = CelestialLight.sample(timeOfDay, level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick), level.getMoonBrightness());
        setUniform(uCelestialDirection, light.x(), light.y(), 0f);
        setUniform(uCelestialColor, light.red(), light.green(), light.blue());
        setUniform(uSkyAmbient, light.ambient());
        // A bounded ray catches ceilings and walls in the light's actual direction.
        var end = origin.add(new Vec3(light.x(), light.y(), 0).scale(96));
        var cameraEntity = camera.getEntity();
        boolean visible = cameraEntity == null
                || level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, cameraEntity)).getType() == HitResult.Type.MISS;
        setUniform(uCelestialVisibility, updateCelestialVisibility(visible ? 1f : 0f, dt));
        float skyExposure = level.getBrightness(LightLayer.SKY, BlockPos.containing(origin)) / 15f;
        setUniform(uLocalSkyExposure, skyExposure);
        // 原版云层遮挡: 太阳/月亮被云挡住时减弱直射反光。
        // 云是连续飘动的, 直接采样会让亮度逐帧跳变, 这里做指数平滑过渡。
        float cloudTarget = cloudOcclusion(level, origin, light, partialTick);
        float cloudBlend = 1f - (float) Math.exp(-Math.max(dt, 0f) / 0.22f);
        smoothedCloudDim += (cloudTarget - smoothedCloudDim) * cloudBlend;
        if (Math.abs(cloudTarget - smoothedCloudDim) < 0.002f) {
            smoothedCloudDim = cloudTarget;
        }
        setUniform(uSunOcclusion, Math.min(skyExposure, smoothedCelestialVisibility) * smoothedCloudDim);
        var skyColor = level.getSkyColor(origin, partialTick);
        float[] sunrise = level.effects().getSunriseColor(timeOfDay, partialTick);
        float sunriseR = 0f, sunriseG = 0f, sunriseB = 0f, sunriseA = 0f;
        if (sunrise != null) {
            sunriseR = sunrise[0];
            sunriseG = sunrise[1];
            sunriseB = sunrise[2];
            sunriseA = sunrise[3];
        }
        setUniform(uSkyZenith, (float) skyColor.x, (float) skyColor.y, (float) skyColor.z);
        setUniform(uSkyHorizon,
                (float) (skyColor.x * (1f - sunriseA * 0.35f) + sunriseR * sunriseA * 0.65f),
                (float) (skyColor.y * (1f - sunriseA * 0.35f) + sunriseG * sunriseA * 0.65f),
                (float) (skyColor.z * (1f - sunriseA * 0.35f) + sunriseB * sunriseA * 0.65f));
        setUniform(uSunHorizon, sunriseR * sunriseA, sunriseG * sunriseA, sunriseB * sunriseA);
        setUniform(uSunAzimuth, light.x() >= 0f ? 1f : -1f, 0f);
        // 手持物按"玩家自己是否被雨淋到"更新(慢变), 世界里的方块/实体按全维度降雨更新,
        // 再由片元着色器用每个表面自己的天空可见度决定接触雨水量, 这样方块不会跟着玩家变湿。
        boolean raining = level.isRaining();
        var wetnessAnchor = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.position() : origin;
        BlockPos wetPos = BlockPos.containing(wetnessAnchor.x, wetnessAnchor.y + 0.2D, wetnessAnchor.z);
        boolean rainAtPlayer = raining && level.canSeeSky(wetPos) && level.isRainingAt(wetPos);
        updateWetnessState(underwater, rainAtPlayer, level.getRainLevel(partialTick), raining);
    }

    private static com.mojang.blaze3d.platform.NativeImage cloudImage;

    /** 原版云层: 云格 = 12 格, 纹理 256x256 每个像素对应一个云格, 云层随游戏时间向 +X 漂移。 */
    private static float cloudOcclusion(net.minecraft.client.multiplayer.ClientLevel level, Vec3 origin,
                                        CelestialLight light, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.getCloudsType() == CloudStatus.OFF) {
            return 1f;
        }
        if (light.y() <= 0.05f || origin.y >= level.effects().getCloudHeight()) {
            return 1f;
        }
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return 1f;
        }
        double t = (cloudHeight - origin.y) / light.y();
        if (t <= 0.0D) {
            return 1f;
        }
        double drift = (level.getGameTime() + partialTick) * 0.03D;
        double sunX = origin.x + light.x() * t;
        double sunZ = origin.z;
        com.mojang.blaze3d.platform.NativeImage image = cloudImage;
        if (image == null) {
            return 1f;
        }
        int size = Math.max(1, image.getWidth());
        int u = (int) Math.floorMod((long) Math.floor((sunX + drift) / 12.0D), size);
        int v = (int) Math.floorMod((long) Math.floor((sunZ + 4.0D) / 12.0D), Math.max(1, image.getHeight()));
        int pixel = image.getPixelRGBA(u, v);
        int alpha = (pixel >>> 24) & 255;
        return 1f - 0.85f * (alpha / 255f);
    }

    private static void reloadCloudTexture() {
        if (cloudImage != null) {
            cloudImage.close();
            cloudImage = null;
        }
        var manager = Minecraft.getInstance().getResourceManager();
        var location = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
        var optional = manager.getResource(location);
        if (optional.isEmpty()) {
            return;
        }
        try (var stream = optional.get().open()) {
            cloudImage = com.mojang.blaze3d.platform.NativeImage.read(stream);
        } catch (Throwable ignored) {
        }
    }

    private static float smoothedCloudDim = 1f;
    /** 潮湿状态(暗化/降粗糙度): 淋雨 5s 变湿, 停雨 8s 阴干; 入水 1s 浸湿。 */
    private static float handWetness, worldWetness;
    /** 水膜细节(波纹): 约 1s 涨满 / 1s 退净, 明显快于潮湿状态。 */
    private static float handWetDetail, worldWetDetail;
    private static long wetnessLastNanos;
    private static boolean wetUnderwater;
    /** 第一人称手部渲染期间提交的几何 = 手持物, 湿润跟随玩家自身而非每个表面的天空可见度。 */
    private static boolean handGeometry;
    private static final float WET_SECONDS = 5.0f;
    private static final float DRY_SECONDS = 8.0f;
    private static final float SUBMERGE_WET_SECONDS = 1.0f;
    private static final float WET_DETAIL_SECONDS = 1.0f;

    /** 手部渲染开始: 期间 resolve() 出来的 RenderType 变体会带上"手持物"标记。 */
    public static void beginHandGeometry() {
        handGeometry = true;
    }

    public static void endHandGeometry() {
        handGeometry = false;
    }

    private static void updateWetnessState(boolean underwater, boolean rainAtPlayer, float rainLevel,
                                           boolean raining) {
        long now = System.nanoTime();
        if (wetnessLastNanos == 0L) {
            wetnessLastNanos = now;
        }
        float dt = Math.min((now - wetnessLastNanos) / 1.0e9f, 0.25f);
        wetnessLastNanos = now;
        wetUnderwater = underwater;
        playerRainExposed = rainAtPlayer;
        handWetness = advanceWetness(handWetness, underwater ? 1f : (rainAtPlayer ? rainLevel : 0f), dt, underwater);
        worldWetness = advanceWetness(worldWetness, underwater ? 1f : (raining ? rainLevel : 0f), dt, underwater);
        handWetDetail = advanceWetDetail(handWetDetail, underwater || rainAtPlayer ? 1f : 0f, dt);
        worldWetDetail = advanceWetDetail(worldWetDetail, underwater || raining ? 1f : 0f, dt);
    }

    /** 世界物体渲染期间压入的"该物体所在位置是否被雨淋到"(原版 isRainingAt 判定, 按需惰性求值)。 */
    private static final java.util.ArrayDeque<RainProbe> RAIN_PROBE_STACK = new java.util.ArrayDeque<>();
    private static boolean playerRainExposed;

    private static final class RainProbe {
        private final java.util.function.BooleanSupplier source;
        private Boolean cached;

        RainProbe(java.util.function.BooleanSupplier source) {
            this.source = source;
        }

        boolean exposed() {
            if (cached == null) {
                cached = source.getAsBoolean();
            }
            return cached;
        }
    }

    public static void pushWorldRainProbe(java.util.function.BooleanSupplier source) {
        RAIN_PROBE_STACK.push(new RainProbe(source));
    }

    public static void popWorldRainProbe() {
        RAIN_PROBE_STACK.poll();
    }

    private static boolean currentWorldRainExposed() {
        RainProbe probe = RAIN_PROBE_STACK.peek();
        return probe == null ? playerRainExposed : probe.exposed();
    }

    private static float advanceWetness(float current, float target, float dt, boolean underwater) {
        if (target > current) {
            return Math.min(target, current + dt / (underwater ? SUBMERGE_WET_SECONDS : WET_SECONDS));
        }
        if (target < current) {
            return Math.max(target, current - dt / DRY_SECONDS);
        }
        return current;
    }

    private static float advanceWetDetail(float current, float target, float dt) {
        if (target > current) {
            return Math.min(target, current + dt / WET_DETAIL_SECONDS);
        }
        return Math.max(target, current - dt / WET_DETAIL_SECONDS);
    }

    private static final float OCCLUSION_FADE_SECONDS = 0.1f;
    private static float smoothedCelestialVisibility = 0f;
    private static long lastFrameNanos;

    private static float frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0f;
        }
        float dt = Math.min((now - lastFrameNanos) / 1.0e9f, 0.25f);
        lastFrameNanos = now;
        return dt;
    }

    private static float updateCelestialVisibility(float target, float dt) {
        smoothedCelestialVisibility = approach(smoothedCelestialVisibility, target, dt);
        return smoothedCelestialVisibility;
    }

    private static float approach(float current, float target, float dt) {
        float step = dt / OCCLUSION_FADE_SECONDS;
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private static final float[] lightColorR = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightColorG = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightColorB = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightStrength = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightTargetStrength = new float[LOCAL_LIGHT_SLOTS];
    /** 每个槽位的世界坐标(供 shader 逐像素算方向), 不再只传相机到光源的方向。 */
    private static final Vec3[] lightSlotPos = new Vec3[LOCAL_LIGHT_SLOTS];
    private static final java.util.List<LightCandidate> LIGHT_CANDIDATES = new java.util.ArrayList<>();
    private static long localLightNextScan = 0L;
    private static long lastPbrDrawAt = 0L;

    private record LightCandidate(double score, Vec3 position, float red, float green, float blue, float target) {
    }

    private static void updateLocalLights(net.minecraft.world.level.Level level, Vec3 origin, float dt) {
        long now = System.currentTimeMillis();
        if (now >= localLightNextScan) {
            localLightNextScan = now + 200L;
            LIGHT_CANDIDATES.clear();
            Minecraft minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            Vec3 visibilityAnchor = player != null ? player.getEyePosition(1.0F) : origin;
            BlockPos center = BlockPos.containing(origin);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 4, 6))) {
                var state = level.getBlockState(pos);
                int emission = state.getLightEmission(level, pos);
                if (emission <= 0) continue;
                BlockPos immutable = pos.immutable();
                if (!isLightVisible(level, visibilityAnchor, immutable, player)) continue;
                double dx = immutable.getX() + 0.5D - origin.x;
                double dy = immutable.getY() + 0.5D - origin.y;
                double dz = immutable.getZ() + 0.5D - origin.z;
                double distSqr = dx * dx + dy * dy + dz * dz;
                double score = emission / (1.0D + distSqr * 0.35D);
                int color = state.getMapColor(level, immutable).col;
                float red = ((color >> 16) & 255) / 255f;
                float green = ((color >> 8) & 255) / 255f;
                float blue = (color & 255) / 255f;
                if (red + green + blue < 0.1f) {
                    red = 1.0f;
                    green = 0.86f;
                    blue = 0.67f;
                }
                LIGHT_CANDIDATES.add(new LightCandidate(score, Vec3.atCenterOf(immutable), red, green, blue,
                        (float) Math.min(1.0D, score / 7.0D)));
            }
            // 开火/镭射/爆炸等动态光只照亮世界, 不参与枪身反光:
            // 枪口闪光离枪太近, 参与反光会变成逐帧闪烁的假高光。
            LIGHT_CANDIDATES.sort((a, b) -> Double.compare(b.score(), a.score()));
            int count = Math.min(LOCAL_LIGHT_SLOTS, LIGHT_CANDIDATES.size());
            boolean[] used = new boolean[count];
            for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
                Vec3 previous = lightSlotPos[slot];
                int bestIndex = -1;
                if (previous != null) {
                    double bestDistance = 2.5D * 2.5D;
                    for (int i = 0; i < count; i++) {
                        if (used[i]) continue;
                        double distance = LIGHT_CANDIDATES.get(i).position().distanceToSqr(previous);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestIndex = i;
                        }
                    }
                }
                if (bestIndex < 0) {
                    for (int i = 0; i < count; i++) {
                        if (!used[i]) {
                            bestIndex = i;
                            break;
                        }
                    }
                }
                if (bestIndex < 0) {
                    lightSlotPos[slot] = null;
                    lightTargetStrength[slot] = 0f;
                    continue;
                }
                used[bestIndex] = true;
                LightCandidate candidate = LIGHT_CANDIDATES.get(bestIndex);
                if (previous == null || previous.distanceToSqr(candidate.position()) > 6.25D) {
                    lightStrength[slot] = Math.min(lightStrength[slot], candidate.target());
                }
                lightSlotPos[slot] = candidate.position();
                lightColorR[slot] = candidate.red();
                lightColorG[slot] = candidate.green();
                lightColorB[slot] = candidate.blue();
                lightTargetStrength[slot] = candidate.target();
            }
        }
        updateLocalLightSlots(dt);
    }

    private static void decayLocalLights(float dt) {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            lightTargetStrength[slot] = 0f;
        }
        updateLocalLightSlots(dt);
    }

    private static void updateLocalLightSlots(float dt) {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            if (lightSlotPos[slot] == null) {
                lightStrength[slot] = approach(lightStrength[slot], 0f, dt);
                if (lightStrength[slot] < 0.001f) {
                    lightStrength[slot] = 0f;
                }
                continue;
            }
            lightStrength[slot] = approach(lightStrength[slot], lightTargetStrength[slot], dt);
        }
    }

    private static void applyLocalLightUniforms() {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            Vec3 pos = lightSlotPos[slot];
            if (pos != null) {
                setUniform(uLocalLightPos[slot], (float) pos.x, (float) pos.y, (float) pos.z);
            } else {
                setUniform(uLocalLightPos[slot], 0f, 0f, 0f);
            }
            setUniform(uLocalLightColor[slot], lightColorR[slot], lightColorG[slot], lightColorB[slot]);
            setUniform(uLocalLightStrength[slot], lightStrength[slot]);
        }
    }

    private static boolean isLightVisible(net.minecraft.world.level.Level level, Vec3 from, BlockPos lightPos,
                                           Entity entity) {
        Vec3 target = new Vec3(lightPos.getX() + 0.5D, lightPos.getY() + 0.5D, lightPos.getZ() + 0.5D);
        Vec3 start = from;
        Vec3 direction = target.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return true;
        }
        Vec3 step = direction.normalize().scale(0.01D);
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit.getBlockPos().equals(lightPos)) {
                return true;
            }
            if (level.getBlockState(hit.getBlockPos()).canOcclude()) {
                return false;
            }
            start = hit.getLocation().add(step);
            if (start.distanceToSqr(target) < 1.0E-6D) {
                return true;
            }
        }
        return false;
    }

    private static Method findOptifine() {
        if (!OptifineCompat.isOptifineInstalled()) return null;
        try {
            return Class.forName("net.optifine.Config").getMethod("isShaders");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean enabled() {
        if (shader == null || !Config.PBR_ENABLED.get() || OculusCompat.isUsingRenderPack()
                || com.ssscript.taczfixes.client.util.IrisShaderCompat.isShaderPackInUse()) return false;
        if (OptifineCompat.isOptifineInstalled()) {
            if (OPTIFINE_SHADERS == null) return false;
            try {
                if ((boolean) OPTIFINE_SHADERS.invoke(null)) return false;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        // TaCZ's AR compatibility entry point selects conventional gun rendering
        // while this shader is active. Do not query it here: that would recurse.
        return true;
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        MATERIALS.clear();
        TYPES.clear();
        CAMO_TYPES.clear();
        com.ssscript.taczfixes.client.camo.DynamicCamo.clear();
        if (camoShader != null) {
            try {
                camoShader.close();
            } catch (Throwable ignored) {
            }
            camoShader = null;
        }
        PbrBloom.release();
        if (mesh != null) { mesh.close(); mesh = null; }
        releaseSceneColor();
        shader = blurShader = compositeShader = maskShader = null;
        clearUniformCache();
        // 资源重载会重新走一遍 RegisterShadersEvent, 云纹理也要跟着换掉。
        reloadCloudTexture();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    // 动态迷彩: 把枪包提供的片段拼进 gun_pbr, 生成带 CamoIndex 分发的程序;
                    // 没有迷彩(或编译失败)时退回原程序, 迷彩行在面板里自然消失。
                    try {
                        com.ssscript.taczfixes.client.camo.CamoShaderBuilder.Result camo =
                                com.ssscript.taczfixes.client.camo.CamoShaderBuilder.build(event.getResourceProvider());
                        com.ssscript.taczfixes.client.camo.DynamicCamo.publish(camo);
                        if (camo != null) {
                            camoShader = camo.shader();
                            shader = camoShader;
                        } else {
                            shader = s;
                        }
                    } catch (Throwable t) {
                        TaczFixesMod.LOGGER.error("taczfixes: dynamic camo build failed, falling back to base gun_pbr", t);
                        com.ssscript.taczfixes.client.camo.DynamicCamo.publish(null);
                        camoShader = null;
                        shader = s;
                    }
                    cacheGunUniforms(shader);
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_mask"), DefaultVertexFormat.POSITION_TEX), s -> maskShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_blur"), DefaultVertexFormat.POSITION_TEX), s -> {
                    blurShader = s;
                    uBlurDirection = s.safeGetUniform("Direction");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_composite"), DefaultVertexFormat.POSITION_TEX), s -> {
                    compositeShader = s;
                    uCompositeStrength = s.safeGetUniform("Strength");
                    uWideStrength = s.safeGetUniform("WideStrength");
                    uHasScopeMask = s.safeGetUniform("HasScopeMask");
                });
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = uScopeDivisionPass = null;
        uReflectionStrength = uGlareStrength = uEmissionStrength = null;
        uGlareDepthTest = uFallbackMaterial = null;
        uCelestialDirection = uCelestialColor = uSkyAmbient = null;
        uCelestialVisibility = uLocalSkyExposure = uWetness = uWetSurface = null;
        uSkyZenith = uSkyHorizon = uSunHorizon = uSunAzimuth = uSunOcclusion = null;
        uDefaultRoughness = uDefaultMetal = uDefaultReflection = uEnvStrength = null;
        uAnisotropy = uWetDetail = uSSRStrength = uTime = null;
        uViewRotX = uViewRotY = uViewRotZ = null;
        uCameraPos = null;
        uBlurDirection = uCompositeStrength = uWideStrength = uHasScopeMask = null;
        uCamoIndex = uCamoStrength = uCamoEmissive = null;
        for (int i = 0; i < LOCAL_LIGHT_SLOTS; i++) {
            uLocalLightPos[i] = null;
            uLocalLightColor[i] = null;
            uLocalLightStrength[i] = null;
        }
    }

    private static void cacheGunUniforms(ShaderInstance s) {
        uHasSpecular = s.safeGetUniform("HasSpecular");
        uHasNormal = s.safeGetUniform("HasNormal");
        uEmissionPass = s.safeGetUniform("EmissionPass");
        uScopeDivisionPass = s.safeGetUniform("ScopeDivisionPass");
        uReflectionStrength = s.safeGetUniform("ReflectionStrength");
        uGlareStrength = s.safeGetUniform("GlareStrength");
        uGlareDepthTest = s.safeGetUniform("GlareDepthTest");
        uEmissionStrength = s.safeGetUniform("EmissionStrength");
        uFallbackMaterial = s.safeGetUniform("FallbackMaterial");
        for (int i = 0; i < LOCAL_LIGHT_SLOTS; i++) {
            int slot = i + 1;
            uLocalLightPos[i] = s.safeGetUniform("LocalLightPos" + slot);
            uLocalLightColor[i] = s.safeGetUniform("LocalLightColor" + slot);
            uLocalLightStrength[i] = s.safeGetUniform("LocalLightStrength" + slot);
        }
        uCelestialDirection = s.safeGetUniform("CelestialDirection");
        uCelestialColor = s.safeGetUniform("CelestialColor");
        uSkyAmbient = s.safeGetUniform("SkyAmbient");
        uCelestialVisibility = s.safeGetUniform("CelestialVisibility");
        uLocalSkyExposure = s.safeGetUniform("LocalSkyExposure");
        uWetness = s.safeGetUniform("Wetness");
        uWetSurface = s.safeGetUniform("WetSurface");
        uSkyZenith = s.safeGetUniform("SkyZenith");
        uSkyHorizon = s.safeGetUniform("SkyHorizon");
        uSunHorizon = s.safeGetUniform("SunHorizon");
        uSunAzimuth = s.safeGetUniform("SunAzimuth");
        uSunOcclusion = s.safeGetUniform("SunOcclusion");
        uDefaultRoughness = s.safeGetUniform("DefaultRoughness");
        uDefaultMetal = s.safeGetUniform("DefaultMetal");
        uDefaultReflection = s.safeGetUniform("DefaultReflection");
        uEnvStrength = s.safeGetUniform("EnvStrength");
        uAnisotropy = s.safeGetUniform("Anisotropy");
        uWetDetail = s.safeGetUniform("WetDetail");
        uSSRStrength = s.safeGetUniform("SSRStrength");
        uViewRotX = s.safeGetUniform("ViewRotX");
        uViewRotY = s.safeGetUniform("ViewRotY");
        uViewRotZ = s.safeGetUniform("ViewRotZ");
        uCameraPos = s.safeGetUniform("CameraPos");
        uTime = s.safeGetUniform("Time");
        uCamoIndex = s.safeGetUniform("CamoIndex");
        uCamoStrength = s.safeGetUniform("CamoStrength");
        uCamoEmissive = s.safeGetUniform("CamoEmissive");
    }

    static void setUniform(AbstractUniform uniform, int value) {
        if (uniform != null) uniform.set(value);
    }

    static void setUniform(AbstractUniform uniform, float value) {
        if (uniform != null) uniform.set(value);
    }

    static void setUniform(AbstractUniform uniform, float x, float y) {
        if (uniform != null) uniform.set(x, y);
    }

    static void setUniform(AbstractUniform uniform, float x, float y, float z) {
        if (uniform != null) uniform.set(x, y, z);
    }

    /** 与材质无关的全局 uniform: 兜底材质参数、天空环境强度、湿润参数与屏幕尺寸。*/
    private static void applyGlobalUniforms(boolean hand, boolean rainExposed) {
        setUniform(uFallbackMaterial, Config.PBR_FALLBACK_MATERIAL.get() ? 1 : 0);
        setUniform(uDefaultRoughness, Config.PBR_DEFAULT_ROUGHNESS.get().floatValue());
        setUniform(uDefaultMetal, Config.PBR_DEFAULT_METAL.get().floatValue());
        setUniform(uDefaultReflection, Config.PBR_DEFAULT_REFLECTION.get().floatValue());
        setUniform(uAnisotropy, Config.PBR_ANISOTROPY.get().floatValue());
        applyWetUniforms(hand, rainExposed);
        boolean ssr = sceneColorCaptured && sceneColorTexture != 0 && sceneDepthTexture != 0
                && Config.PBR_SSR.get() && Config.PBR_SSR_STRENGTH.get() > 0.0D;
        setUniform(uSSRStrength, ssr ? Config.PBR_SSR_STRENGTH.get().floatValue() : 0f);
        if (ssr) {
            RenderSystem.setShaderTexture(6, sceneColorTexture);
            RenderSystem.setShaderTexture(7, sceneDepthTexture);
        }
    }

    /**
     * 湿润量分两套: 手持物用玩家自身的淋雨/入水状态(慢变), 世界物体用全维度降雨的慢变状态,
     * 再用物体提交几何时算好的"是否被雨淋到"(原版 isRainingAt, 含高度图/群系判定)决定是否吃雨。
     * 湿细节(水膜波纹)走 1s 快速包络, 涨落比潮湿状态快得多。
     */
    private static void applyWetUniforms(boolean hand, boolean rainExposed) {
        boolean detail = Config.PBR_WET_DETAIL.get();
        if (hand) {
            setUniform(uWetness, handWetness);
            setUniform(uWetDetail, detail ? handWetDetail : 0f);
            setUniform(uWetSurface, 1f);
        } else {
            setUniform(uWetness, worldWetness);
            setUniform(uWetDetail, detail ? worldWetDetail : 0f);
            setUniform(uWetSurface, rainExposed || wetUnderwater ? 1f : 0f);
        }
    }

    private static float materialReflectionStrength(Material material) {
        if (material.specular() != null) {
            return Config.PBR_REFLECTION.get().floatValue();
        }
        return Config.PBR_FALLBACK_MATERIAL.get() ? Config.PBR_DEFAULT_REFLECTION.get().floatValue() : 0f;
    }

    private static float materialGlareStrength(Material material) {
        if (material.specular() != null) {
            return Config.PBR_GLARE_STRENGTH.get().floatValue();
        }
        return Config.PBR_FALLBACK_MATERIAL.get() ? Config.PBR_GLARE_STRENGTH.get().floatValue() * 0.6f : 0f;
    }

    static void setBlurDirection(float x, float y) {
        if (uBlurDirection != null) uBlurDirection.set(x, y);
    }

    static void setBloomStrength(float value) {
        if (uCompositeStrength != null) uCompositeStrength.set(value);
    }

    static void setWideBloomStrength(float value) {
        if (uWideStrength != null) uWideStrength.set(value);
    }

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    static void applyMaterialState(Material material, boolean hand, boolean rainExposed) {
        if (shader == null) {
            return;
        }
        if (material.specular() != null) {
            RenderSystem.setShaderTexture(3, material.specular());
        }
        if (material.normal() != null) {
            RenderSystem.setShaderTexture(4, material.normal());
        }
        applyGlobalUniforms(hand, rainExposed);
        setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
        setUniform(uHasNormal, material.normal() == null ? 0 : 1);
        setUniform(uReflectionStrength, materialReflectionStrength(material));
        setUniform(uGlareStrength, materialGlareStrength(material));
        setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
        setUniform(uEmissionPass, 0);
        setUniform(uScopeDivisionPass, 0);
        RenderSystem.setShader(() -> shader);
        bindGlareDepth();
    }

    private static void bindGlareDepth() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        boolean mainTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == main.frameBufferId;
        if (mainTarget) {
            RenderSystem.setShaderTexture(5, main.getDepthTextureId());
        }
        setUniform(uGlareDepthTest, mainTarget ? 1 : 0);
    }

    /**
     * 场景渲染结束(手部之前)复制一份主目标颜色与深度, 供屏幕空间反射采样。
     * 复制到独立纹理可避免渲染枪械时采样正在写入的颜色附件。
     */
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        sceneColorCaptured = false;
        if (!Config.PBR_SSR.get() || shader == null || !enabled()) {
            return;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        if (sceneColorTexture == 0) {
            sceneColorTexture = com.mojang.blaze3d.platform.GlStateManager._genTexture();
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(sceneColorTexture);
        if (sceneColorWidth != main.width || sceneColorHeight != main.height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, main.width, main.height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            sceneColorWidth = main.width;
            sceneColorHeight = main.height;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, main.width, main.height);
        // 世界深度(手部渲染会清空主目标深度, 所以必须在手部之前复制一份)。
        if (sceneDepthTexture == 0) {
            sceneDepthTexture = com.mojang.blaze3d.platform.GlStateManager._genTexture();
        }
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(sceneDepthTexture);
        if (sceneDepthWidth != main.width || sceneDepthHeight != main.height) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24, main.width, main.height, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            sceneDepthWidth = main.width;
            sceneDepthHeight = main.height;
        }
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, main.width, main.height);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(previousTexture);
        sceneColorCaptured = true;
    }

    private static void releaseSceneColor() {
        if (sceneColorTexture != 0) {
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(sceneColorTexture);
            sceneColorTexture = 0;
        }
        if (sceneDepthTexture != 0) {
            com.mojang.blaze3d.platform.GlStateManager._deleteTexture(sceneDepthTexture);
            sceneDepthTexture = 0;
        }
        sceneColorWidth = 0;
        sceneColorHeight = 0;
        sceneDepthWidth = 0;
        sceneDepthHeight = 0;
        sceneColorCaptured = false;
    }

    public static RenderType resolve(RenderType original) {
        if (!enabled() || original instanceof PbrType || original.format() != DefaultVertexFormat.NEW_ENTITY
                || !(original instanceof PbrCompositeAccessor composite)) return original;
        CamoUniforms camo = camoSuppression > 0 ? CamoUniforms.NONE : camoForCurrentItem();
        boolean hand = handGeometry;
        boolean rainExposed = currentWorldRainExposed();
        if (materialFallbackSuppression > 0) {
            return resolveType(original, composite, false, camo, hand, rainExposed);
        }
        if (camo.index() > 0) {
            CamoKey key = new CamoKey(original, camo.index(), camo.strength(), camo.emissive(), hand, rainExposed);
            return CAMO_TYPES.computeIfAbsent(key,
                    k -> resolveType(k.original(), composite, true, camo, k.hand(), k.rainExposed()));
        }
        TypeKey key = new TypeKey(original, hand, rainExposed);
        return TYPES.computeIfAbsent(key,
                k -> resolveType(k.original(), composite, true, camo, k.hand(), k.rainExposed()));
    }

    /** 当前正在渲染的枪/配件是否挂了动态迷彩(读渲染上下文的 NBT)。 */
    private static CamoUniforms camoForCurrentItem() {
        if (camoShader == null) {
            return CamoUniforms.NONE;
        }
        // 配件渲染时一律跟随它所属的枪的迷彩: 迷彩归属枪, 而不是单个配件(不再读配件自己的 NBT)。
        net.minecraft.world.item.ItemStack camoOwner =
                com.ssscript.taczfixes.client.util.GunRecolorManager.currentCamoSource();
        if (camoOwner == null) {
            camoOwner = com.ssscript.taczfixes.client.util.GunRecolorManager.currentRenderItem();
        }
        com.ssscript.taczfixes.client.camo.DynamicCamo.Entry entry =
                com.ssscript.taczfixes.client.camo.DynamicCamo.appliedOn(camoOwner);
        if (entry == null) {
            return CamoUniforms.NONE;
        }
        return new CamoUniforms(entry.index(), entry.strength(), entry.emissive());
    }

    private static void applyCamoUniforms(CamoUniforms camo) {
        setUniform(uCamoIndex, camo.index());
        setUniform(uCamoStrength, camo.strength());
        setUniform(uCamoEmissive, camo.emissive());
    }

    private static RenderType resolveType(RenderType original, PbrCompositeAccessor composite,
                                          boolean allowFallback, CamoUniforms camo,
                                          boolean hand, boolean rainExposed) {
        var textureState = ((PbrStateAccessor) (Object) composite.taczfixes$pbrState()).taczfixes$pbrTexture();
        var texture = ((PbrTextureAccessor) textureState).taczfixes$pbrTextureLocation();
        if (texture.isEmpty()) return original;
        ResourceLocation base = com.ssscript.taczfixes.client.util.GunRecolorManager.sourceOf(texture.get());
        Material material = material(base);
        if (allowFallback && material != null && material.specular() == null && material.normal() == null) {
            ResourceLocation own = com.ssscript.taczfixes.client.util.GunRecolorManager.currentBaseTexture();
            if (own != null && !own.equals(base)) {
                Material ownMaterial = material(own);
                if (ownMaterial != null && (ownMaterial.specular() != null || ownMaterial.normal() != null)) {
                    material = ownMaterial;
                }
            }
        }
        return material == null ? original : new PbrType(original, material, camo, hand, rainExposed);
    }

    private static Material material(ResourceLocation base) {
        return MATERIALS.computeIfAbsent(base, PbrRenderer::findMaterial).orElse(null);
    }

    private static Optional<Material> findMaterial(ResourceLocation base) {
        String path = base.getPath();
        if (!path.endsWith(".png")) return Optional.empty();
        String stem = path.substring(0, path.length() - 4);
        ResourceLocation specular = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + "_s.png");
        ResourceLocation normal = ResourceLocation.fromNamespaceAndPath(base.getNamespace(), stem + "_n.png");
        var resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation specularLocation = resources.getResource(specular).isPresent() ? specular : null;
        return Optional.of(new Material(specularLocation,
                resources.getResource(normal).isPresent() ? normal : null,
                specularLocation != null && hasEmissionPixels(specularLocation)));
    }

    /**
     * LabPBR 的 _s 贴图 alpha 通道是自发光强度: 0 与 255 都表示不发光, 1~254 才会发光。
     * 扫一遍贴图判断这张材质是否可能产生泛光, 用来在没有自发光内容的枪上完全跳过泛光链路。
     * 结果随 {@link Material} 缓存(每张贴图只扫一次), 不在渲染循环里做。
     */
    private static boolean hasEmissionPixels(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                return false;
            }
            try (var input = resource.get().open();
                 com.mojang.blaze3d.platform.NativeImage image =
                         com.mojang.blaze3d.platform.NativeImage.read(
                                 com.mojang.blaze3d.platform.NativeImage.Format.RGBA, input)) {
                // 与 GunTransparency 相同的取 alpha 方式(渲染线程上一次性执行, 随后随 Material 缓存)。
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getPixelRGBA(x, y) >>> 24;
                        if (alpha > 0 && alpha < 255) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 读不出来就当没有自发光: 宁可少一层泛光也不要出错。
        }
        return false;
    }

    private static final class PbrType extends RenderType {
        private final RenderType original;
        private final Material material;
        private final CamoUniforms camo;
        private final boolean hand;
        private final boolean rainExposed;

        PbrType(RenderType original, Material material, CamoUniforms camo, boolean hand, boolean rainExposed) {
            super("taczfixes_pbr", original.format(), original.mode(), original.bufferSize(),
                    original.affectsCrumbling(), true, original::setupRenderState, original::clearRenderState);
            this.original = original;
            this.material = material;
            this.camo = camo;
            this.hand = hand;
            this.rainExposed = rainExposed;
        }

        @Override public Optional<RenderType> outline() { return original.outline(); }

        @Override
        public void setupRenderState() {
            original.setupRenderState();
            bindGlareDepth();
            boolean accelerated = isAcceleratedPbrPass();
            if (accelerated) {
                applyMaterialState(material, hand, rainExposed);
                applyCamoUniforms(camo);
            }
        }

        @Override
        public void end(BufferBuilder builder, VertexSorting sorting) {
            if (!builder.building()) return;
            // Keep vanilla ordering for translucent gun skins, including on the emissive pass.
            if (mode() == VertexFormat.Mode.QUADS) builder.setQuadSorting(sorting);
            var data = builder.endOrDiscardIfEmpty();
            if (data == null) return;
            // 顶点数据在 upload 之后会被释放, 所以自发光来源标记必须在 upload 之前做。
            if (material.emissive() || camo.emissive() > 0f) {
                markEmissionSource();
            }
            if (mesh == null) mesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            setupRenderState();
            ShaderInstance previous = RenderSystem.getShader();
            int specularSlot = RenderSystem.getShaderTexture(3);
            int normalSlot = RenderSystem.getShaderTexture(4);
            int depthSlot = RenderSystem.getShaderTexture(5);
            int sceneSlot = RenderSystem.getShaderTexture(6);
            int sceneDepthSlot = RenderSystem.getShaderTexture(7);
            try {
                mesh.bind();
                mesh.upload(data);
                if (!enabled()) {
                    mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), previous);
                    return;
                }
                lastPbrDrawAt = System.currentTimeMillis();
                if (material.specular() != null) RenderSystem.setShaderTexture(3, material.specular());
                if (material.normal() != null) RenderSystem.setShaderTexture(4, material.normal());
                applyGlobalUniforms(hand, rainExposed);
                setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
                setUniform(uHasNormal, material.normal() == null ? 0 : 1);
                setUniform(uReflectionStrength, materialReflectionStrength(material));
                setUniform(uGlareStrength, materialGlareStrength(material));
                setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
                setUniform(uEmissionPass, 0);
                applyCamoUniforms(camo);

                RenderSystem.setShader(() -> shader);
                bindGlareDepth();
                int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                int mainFbo = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
                boolean tracerAllowed = !isTracerBloomSuppressed() || Config.PBR_BLOOM_TRACER.get();
                if (!emissionReplay) {
                    // MRT: 主绘制同一遍着色同时把发光写进主目标的第二颜色附件, 不再单独重画一遍几何。
                    boolean emissionWrite = tracerAllowed
                            && PbrBloom.beginEmissionWrite(previousFbo == mainFbo);
                    try {
                        mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                    } finally {
                        if (emissionWrite) {
                            PbrBloom.endEmissionWrite();
                        }
                    }
                    if (!emissionWrite && tracerAllowed && previousFbo == mainFbo
                            && PbrBloom.mrtUnavailable() && PbrBloom.beginFallbackCapture()) {
                        try {
                            drawEmissionFallback(shader);
                        } finally {
                            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                        }
                    }
                } else if (tracerAllowed && previousFbo == mainFbo && PbrBloom.beginFallbackCapture()) {
                    // 加速渲染时主绘制被跳过, 这里补画一次发光(只写发光目标, 不碰主画面)。
                    try {
                        drawEmissionFallback(shader);
                    } finally {
                        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                    }
                }            } finally {
                VertexBuffer.unbind();
                RenderSystem.setShaderTexture(3, specularSlot);
                RenderSystem.setShaderTexture(4, normalSlot);
                RenderSystem.setShaderTexture(5, depthSlot);
                RenderSystem.setShaderTexture(6, sceneSlot);
                RenderSystem.setShaderTexture(7, sceneDepthSlot);
                RenderSystem.setShader(() -> previous);
                clearRenderState();
            }
        }

    }

        /** 只写发光目标的补画(MRT 不可用时 / 加速渲染主绘制被跳过时)。 */
        private static void drawEmissionFallback(ShaderInstance shader) {
            boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthTestWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int depthFuncWas = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            boolean stencilWas = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            try {
                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                pushEmissionBlendOverride();
                setUniform(uEmissionPass, 1);
                setUniform(uScopeDivisionPass, 0);
                mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            } finally {
                setUniform(uEmissionPass, 0);
                setUniform(uScopeDivisionPass, 0);
                popEmissionBlendOverride();
                if (blendWas) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }
                if (depthTestWas) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                RenderSystem.depthFunc(depthFuncWas);
                RenderSystem.depthMask(depthMask);
                if (stencilWas) {
                    GL11.glEnable(GL11.GL_STENCIL_TEST);
                } else {
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                }
            }
        }

    private PbrRenderer() {}
}
