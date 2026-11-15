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
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness;
    private static AbstractUniform uSkyZenith, uSkyHorizon, uSunHorizon, uSunAzimuth, uSunOcclusion;
    private static AbstractUniform uDefaultRoughness, uDefaultMetal, uDefaultReflection, uEnvStrength;
    private static AbstractUniform uAnisotropy, uWetDetail, uSSRStrength, uTime;
    private static AbstractUniform uViewRotX, uViewRotY, uViewRotZ;
    private static AbstractUniform uCameraPos;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uHasScopeMask;
    private static VertexBuffer mesh;
    private static int emissionBlendOverride;
    private static boolean acceleratedPbrPass;
    private static boolean emissionReplay;
    private static final int MATERIALS_MAX = 256;

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
        return enabled() && PbrBloom.canCapture() && !isAcceleratedPbrPass()
                && com.tacz.guns.compat.ar.ARCompat.shouldAccelerate()
                && resolve(type) instanceof PbrType;
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
    private static final Map<RenderType, RenderType> TYPES =
            new java.util.LinkedHashMap<>(TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RenderType, RenderType> eldest) {
                    return size() > TYPES_MAX;
                }
            };
    private static final Method OPTIFINE_SHADERS = findOptifine();

    private record Material(ResourceLocation specular, ResourceLocation normal) {}

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
        setUniform(uWetDetail, Config.PBR_WET_DETAIL.get() ? 1f : 0f);
        if (!level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            setUniform(uCelestialDirection, 0f, 1f, 0f);
            setUniform(uCelestialColor, 0f, 0f, 0f);
            setUniform(uSkyAmbient, 0f);
            setUniform(uCelestialVisibility, updateCelestialVisibility(0f, dt));
            setUniform(uLocalSkyExposure, 0f);
            setUniform(uWetness, updateWetness(0f));
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
        float targetWetness = 0f;
        var wetnessAnchor = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.position() : origin;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isRaining()) {
            BlockPos wetPos = BlockPos.containing(wetnessAnchor.x, wetnessAnchor.y + 0.2D, wetnessAnchor.z);
            if (level.canSeeSky(wetPos) && level.isRainingAt(wetPos)) {
                targetWetness = level.getRainLevel(partialTick);
            }
        }
        setUniform(uWetness, updateWetness(targetWetness));
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
    private static float smoothedWetness = 0f;
    private static long wetnessLastNanos;
    private static final float WET_SECONDS = 5.0f;
    private static final float DRY_SECONDS = 8.0f;

    private static float updateWetness(float target) {
        long now = System.nanoTime();
        if (wetnessLastNanos == 0L) {
            wetnessLastNanos = now;
        }
        float dt = Math.min((now - wetnessLastNanos) / 1.0e9f, 0.25f);
        wetnessLastNanos = now;
        if (target > smoothedWetness) {
            smoothedWetness = Math.min(target, smoothedWetness + dt / WET_SECONDS);
        } else if (target < smoothedWetness) {
            smoothedWetness = Math.max(target, smoothedWetness - dt / DRY_SECONDS);
        }
        return smoothedWetness;
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
        PbrBloom.release();
        if (mesh != null) { mesh.close(); mesh = null; }
        releaseSceneColor();
        shader = blurShader = compositeShader = maskShader = null;
        clearUniformCache();
        // 资源重载会重新走一遍 RegisterShadersEvent, 云纹理也要跟着换掉。
        reloadCloudTexture();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    shader = s;
                    cacheGunUniforms(s);
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
                    uHasScopeMask = s.safeGetUniform("HasScopeMask");
                });
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = uScopeDivisionPass = null;
        uReflectionStrength = uGlareStrength = uEmissionStrength = null;
        uGlareDepthTest = uFallbackMaterial = null;
        uCelestialDirection = uCelestialColor = uSkyAmbient = null;
        uCelestialVisibility = uLocalSkyExposure = uWetness = null;
        uSkyZenith = uSkyHorizon = uSunHorizon = uSunAzimuth = uSunOcclusion = null;
        uDefaultRoughness = uDefaultMetal = uDefaultReflection = uEnvStrength = null;
        uAnisotropy = uWetDetail = uSSRStrength = uTime = null;
        uViewRotX = uViewRotY = uViewRotZ = null;
        uCameraPos = null;
        uBlurDirection = uCompositeStrength = uHasScopeMask = null;
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

    /** 与材质无关的全局 uniform: 兜底材质参数、天空环境强度、SSR 与屏幕尺寸。*/
    private static void applyGlobalUniforms() {
        setUniform(uFallbackMaterial, Config.PBR_FALLBACK_MATERIAL.get() ? 1 : 0);
        setUniform(uDefaultRoughness, Config.PBR_DEFAULT_ROUGHNESS.get().floatValue());
        setUniform(uDefaultMetal, Config.PBR_DEFAULT_METAL.get().floatValue());
        setUniform(uDefaultReflection, Config.PBR_DEFAULT_REFLECTION.get().floatValue());
        setUniform(uAnisotropy, Config.PBR_ANISOTROPY.get().floatValue());
        setUniform(uWetDetail, Config.PBR_WET_DETAIL.get() ? 1f : 0f);
        boolean ssr = sceneColorCaptured && sceneColorTexture != 0 && sceneDepthTexture != 0
                && Config.PBR_SSR.get() && Config.PBR_SSR_STRENGTH.get() > 0.0D;
        setUniform(uSSRStrength, ssr ? Config.PBR_SSR_STRENGTH.get().floatValue() : 0f);
        if (ssr) {
            RenderSystem.setShaderTexture(6, sceneColorTexture);
            RenderSystem.setShaderTexture(7, sceneDepthTexture);
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

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    static void applyMaterialState(Material material) {
        if (shader == null) {
            return;
        }
        if (material.specular() != null) {
            RenderSystem.setShaderTexture(3, material.specular());
        }
        if (material.normal() != null) {
            RenderSystem.setShaderTexture(4, material.normal());
        }
        applyGlobalUniforms();
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
        if (materialFallbackSuppression > 0) {
            return resolveType(original, composite, false);
        }
        return TYPES.computeIfAbsent(original, key -> resolveType(key, composite, true));
    }

    private static RenderType resolveType(RenderType original, PbrCompositeAccessor composite, boolean allowFallback) {
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
        return material == null ? original : new PbrType(original, material);
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
                resources.getResource(normal).isPresent() ? normal : null));
    }

    private static final class PbrType extends RenderType {
        private final RenderType original;
        private final Material material;

        PbrType(RenderType original, Material material) {
            super("taczfixes_pbr", original.format(), original.mode(), original.bufferSize(),
                    original.affectsCrumbling(), true, original::setupRenderState, original::clearRenderState);
            this.original = original;
            this.material = material;
        }

        @Override public Optional<RenderType> outline() { return original.outline(); }

        @Override
        public void setupRenderState() {
            original.setupRenderState();
            bindGlareDepth();
            boolean accelerated = isAcceleratedPbrPass();
            if (accelerated) {
                applyMaterialState(material);
            }
        }

        @Override
        public void end(BufferBuilder builder, VertexSorting sorting) {
            if (!builder.building()) return;
            // Keep vanilla ordering for translucent gun skins, including on the emissive pass.
            if (mode() == VertexFormat.Mode.QUADS) builder.setQuadSorting(sorting);
            var data = builder.endOrDiscardIfEmpty();
            if (data == null) return;
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
                applyGlobalUniforms();
                setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
                setUniform(uHasNormal, material.normal() == null ? 0 : 1);
                setUniform(uReflectionStrength, materialReflectionStrength(material));
                setUniform(uGlareStrength, materialGlareStrength(material));
                setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
                setUniform(uEmissionPass, 0);
                RenderSystem.setShader(() -> shader);
                bindGlareDepth();
                if (!emissionReplay) {
                    mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                }
                int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                int mainFbo = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
                boolean lensDivision = previousFbo != mainFbo && isScopeDivision() && PbrBloom.pureScopeAiming();
                if (PbrBloom.canCapture()
                        && (!isTracerBloomSuppressed() || Config.PBR_BLOOM_TRACER.get())
                        && (previousFbo == mainFbo || lensDivision)) {
                    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                    boolean stencilWas = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
                    int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
                    int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
                    int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
                    int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
                    int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
                    int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
                    int[] viewport = new int[4];
                    GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                    boolean replaceEmission = !isScopeDivision();
                    boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
                    boolean depthTestWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                    int depthFuncWas = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
                    if (replaceEmission) {
                        // The emission target must keep the nearest surface, not add every
                        // PBR draw. Additive blending made bloom from a rear workbench leak
                        // through a workbench rendered in front of it.
                        pushEmissionBlendOverride();
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    }
                    try {
                        RenderSystem.depthMask(true);
                        PbrBloom.bindCapture();
                        // The emission target carries a fresh copy of the main stencil,
                        // so the draw's own stencil test keeps sight divisions visible.
                        // Scope lens suppression only needs to cull everything outside
                        // the division.
                        if (PbrBloom.pureScopeAiming() && PbrBloom.scopeStencilAvailable() && !isScopeDivision()) {
                            GL11.glEnable(GL11.GL_STENCIL_TEST);
                            GL11.glStencilFunc(GL11.GL_EQUAL, 0, 255);
                            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                        }
                        setUniform(uEmissionPass, 1);
                        setUniform(uScopeDivisionPass, isScopeDivision() ? 1 : 0);
                        mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                    } finally {
                        if (replaceEmission) {
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
                        }
                        setUniform(uEmissionPass, 0);
                        setUniform(uScopeDivisionPass, 0);
                        if (stencilWas) {
                            GL11.glEnable(GL11.GL_STENCIL_TEST);
                            GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                            GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
                        } else {
                            GL11.glDisable(GL11.GL_STENCIL_TEST);
                        }
                        RenderSystem.depthMask(depthMask);
                        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                    }
                }
            } finally {
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

    private PbrRenderer() {}
}
