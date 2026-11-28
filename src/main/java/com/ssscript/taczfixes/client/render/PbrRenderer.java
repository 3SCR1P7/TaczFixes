package com.ssscript.taczfixes.client.render;
import com.ssscript.taczfixes.client.mixin.shader.PbrCompositeAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrStateAccessor;
import com.ssscript.taczfixes.client.mixin.shader.PbrTextureAccessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.MeshData;
import com.ssscript.taczfixes.TaczFixesMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@net.neoforged.fml.common.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader, smoothShader;
    private static AbstractUniform uHasSpecular, uHasNormal, uEmissionPass;
    private static AbstractUniform uReflectionStrength, uGlareStrength, uEmissionStrength;
    private static AbstractUniform uGlareDepthTest, uFallbackMaterial;
    private static final int LOCAL_LIGHT_SLOTS = 4;
    private static final AbstractUniform[] uLocalLightDir = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static final AbstractUniform[] uLocalLightColor = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static final AbstractUniform[] uLocalLightStrength = new AbstractUniform[LOCAL_LIGHT_SLOTS];
    private static AbstractUniform uCelestialDirection, uCelestialColor, uSkyAmbient;
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness, uWetSurface;
    private static AbstractUniform uSkyZenith, uSkyHorizon, uSunHorizon, uSunAzimuth, uSunOcclusion;
    private static AbstractUniform uDefaultRoughness, uDefaultMetal, uDefaultReflection, uEnvStrength;
    private static AbstractUniform uAnisotropy, uWetDetail, uSSRStrength, uTime;
    private static AbstractUniform uMetalnessBoost, uRoughnessBoost;
    private static AbstractUniform uSSRSteps, uScreenSize;
    private static AbstractUniform uViewRotX, uViewRotY, uViewRotZ;
    private static AbstractUniform uCameraPos;
    private static AbstractUniform uCamoIndex, uCamoStrength, uCamoEmissive;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uWideStrength, uHasScopeMask;
    private static AbstractUniform uHasSeparateBloomDepth;
    private static AbstractUniform uCompositeScissorRect, uCompositeScissorFade, uCompositeHasScissor;
    private static AbstractUniform uSmoothAlpha;
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

    /**
     * 加速渲染主绘制里写出的发光是否已被验证为"真的落到了发光附件上"。
     * 验证通过前不关闭补画, 避免驱动/状态原因导致泛光直接消失。
     */
    private static boolean acceleratedEmissionVerified;
    private static int acceleratedEmissionVerifyAttempts;
    private static int acceleratedEmissionWrites;

    public static boolean shouldReplayEmission(RenderType type) {
        // 加速路径的主绘制已验证能写出泛光光源: 不再为泛光把整个模型补画一遍。
        if (acceleratedEmissionVerified) {
            return false;
        }
        return !isEmissionReplay() && !isAcceleratedPbrPass() && PbrBloom.configured()
                && com.tacz.guns.compat.ar.ARCompat.shouldAccelerate()
                && resolve(type) instanceof PbrType;
    }

    /** 是否还需要一次回读验证; 最多尝试几次, 失败就继续走补画, 不影响观感。 */
    static boolean requestAcceleratedEmissionVerify() {
        if (acceleratedEmissionVerified || acceleratedEmissionWrites <= 0
                || acceleratedEmissionVerifyAttempts >= 3) {
            return false;
        }
        acceleratedEmissionVerifyAttempts++;
        return true;
    }

    /** 回读确认发光附件里真的有像素被写入(alpha = 1)后, 才算 AR 路径的发光可用。 */
    static void confirmAcceleratedEmission() {
        acceleratedEmissionVerified = true;
    }

    /** 本模型是否有自发光骨骼(部件或其父链), 用于避免给不可能发光的模型补画。 */
    public static boolean modelHasIlluminated(java.util.List<? extends com.tacz.guns.client.model.bedrock.BedrockPart> parts) {
        if (parts == null) {
            return false;
        }
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : parts) {
            for (com.tacz.guns.client.model.bedrock.BedrockPart node = part; node != null; node = node.getParent()) {
                if (node.illuminated) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 这个渲染类型背后的材质本身是否会发光(_s 贴图 alpha / 发光迷彩)。 */
    public static boolean emissionPossible(RenderType type) {
        RenderType resolved = resolve(type);
        if (resolved instanceof PbrType pbr) {
            return pbr.material.emissive() || pbr.camo.emissive() > 0f;
        }
        return false;
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

    /** 不同迷彩(以及不同强度)的枪共用同一张贴图, 但必须分开成不同 RenderType, 才能各带各的 uniform。 */
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

    private record Material(ResourceLocation specular, ResourceLocation normal, boolean emissive) {}

    /**
     * 本帧是否出现过真正的自发光来源: 自发光骨骼({@code *_illuminated})、自发光贴图(_s alpha)、
     * 镭射光束、曳光弹、发光迷彩。泛光链路只在这类内容存在时才跑, 没有自发光时完全跳过。
     */
    private static boolean frameEmission;

    /** 每帧开头清空自发光来源标记(由 {@link PbrBloom#beginFrame()} 调用)。 */
    public static void beginFrame() {
        frameEmission = false;
        enabledFrameValid = false;
        acceleratedEmissionWrites = 0;
        // 这些抑制计数器是 push/pop 配对的临时状态: 万一某次渲染异常导致 pop 丢失,
        // 不清零会让迷彩/材质兜底在本局游戏里永久失效。
        materialFallbackSuppression = 0;
        camoSuppression = 0;
        emissionBlendOverride = 0;
        beginEmissionBoundsFrame();
        // 加速路径的发光在主绘制里写出, 拿不到顶点包围盒: 清掉上一帧的包围盒, 防止用过期区域裁掉光晕。
        if (acceleratedEmissionVerified) {
            previousBoundsValid = false;
        }
    }

    // ----- 自发光几何的屏幕包围盒(用于泛光 mask/模糊/合成的 glScissor 裁剪) -----
    private static boolean emissionBoundsValid;
    private static boolean emissionBoundsUnknown;
    private static final org.joml.Matrix4f EMISSION_CLIP_MATRIX = new org.joml.Matrix4f();
    private static float emissionMinX, emissionMaxX, emissionMinY, emissionMaxY;
    private static boolean previousBoundsValid;
    private static float previousMinX, previousMaxX, previousMinY, previousMaxY;

    private static void beginEmissionBoundsFrame() {
        if (emissionBoundsValid && !emissionBoundsUnknown) {
            previousBoundsValid = true;
            previousMinX = emissionMinX;
            previousMaxX = emissionMaxX;
            previousMinY = emissionMinY;
            previousMaxY = emissionMaxY;
        } else {
            previousBoundsValid = false;
        }
        emissionBoundsValid = false;
        emissionBoundsUnknown = false;
        emissionMinX = Float.POSITIVE_INFINITY;
        emissionMaxX = Float.NEGATIVE_INFINITY;
        emissionMinY = Float.POSITIVE_INFINITY;
        emissionMaxY = Float.NEGATIVE_INFINITY;
    }

    /**
     * 检查这批几何是否真的产生自发光像素, 同时累积这些顶点的屏幕 NDC 包围盒。
     * 已启用发光写入的批次中, 非自发光面也可能写入眩光和深度, 所以包围盒包含整个批次。
     */
    private static boolean analyzeEmissionGeometry(MeshData data, boolean allEmitted) {
        // 泛光链路没开时发光附件不会写: 这批几何既不需要标记, 也不需要包围盒, 直接跳过整趟扫描。
        if (!PbrBloom.configured()) {
            return false;
        }
        boolean emitted = allEmitted;
        try {
            var state = data.drawState();
            var format = state.format();
            int stride = format.getVertexSize();
            int vertexCount = state.vertexCount();
            ByteBuffer buffer = data.vertexBuffer();
            if (stride <= 0 || vertexCount <= 0 || buffer == null) {
                emissionBoundsUnknown |= emitted;
                return allEmitted;
            }
            int positionOffset = -1;
            int uv2Offset = -1;
            int offset = 0;
            for (VertexFormatElement element : format.getElements()) {
                if (element.usage() == VertexFormatElement.Usage.POSITION) {
                    positionOffset = offset;
                } else if (element.usage() == VertexFormatElement.Usage.UV && element.index() == 2) {
                    uv2Offset = offset;
                }
                offset += element.byteSize();
            }
            if (!emitted && uv2Offset >= 0) {
                for (int i = 0; i < vertexCount && !emitted; i++) {
                    emitted = (buffer.getShort(i * stride + uv2Offset) & 1) != 0;
                }
            }
            if (!emitted || emissionBoundsUnknown) {
                return emitted;
            }
            if (positionOffset < 0) {
                emissionBoundsUnknown = true;
                return true;
            }
            var projection = EMISSION_CLIP_MATRIX.set(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix());
            float p00 = projection.m00(), p10 = projection.m10(), p20 = projection.m20(), p30 = projection.m30();
            float p01 = projection.m01(), p11 = projection.m11(), p21 = projection.m21(), p31 = projection.m31();
            float p03 = projection.m03(), p13 = projection.m13(), p23 = projection.m23(), p33 = projection.m33();
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < vertexCount; i++) {
                int base = i * stride;
                float x = buffer.getFloat(base + positionOffset);
                float y = buffer.getFloat(base + positionOffset + 4);
                float z = buffer.getFloat(base + positionOffset + 8);
                float clipX = p00 * x + p10 * y + p20 * z + p30;
                float clipY = p01 * x + p11 * y + p21 * z + p31;
                float clipW = p03 * x + p13 * y + p23 * z + p33;
                if (!Float.isFinite(clipW) || clipW <= 1.0E-5F) {
                    // A primitive crossing the camera plane cannot be bounded by its projected vertices.
                    emissionBoundsUnknown = true;
                    return true;
                }
                float ndcX = clipX / clipW;
                float ndcY = clipY / clipW;
                if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
                    emissionBoundsUnknown = true;
                    return true;
                }
                minX = Math.min(minX, ndcX);
                maxX = Math.max(maxX, ndcX);
                minY = Math.min(minY, ndcY);
                maxY = Math.max(maxY, ndcY);
            }
            emissionBoundsValid = true;
            emissionMinX = Math.min(emissionMinX, minX);
            emissionMaxX = Math.max(emissionMaxX, maxX);
            emissionMinY = Math.min(emissionMinY, minY);
            emissionMaxY = Math.max(emissionMaxY, maxY);
            return true;
        } catch (Throwable ignored) {
            emissionBoundsUnknown |= emitted;
            return emitted;
        }
    }

    /** 发光屏幕区域换算成指定渲染目标的 scissor 矩形; marginPixels 以主分辨率像素计。 */
    static boolean emissionScissorRect(int targetWidth, int targetHeight, int mainWidth, int mainHeight,
                                       float marginPixels, int[] out) {
        boolean hasCurrent = emissionBoundsValid;
        boolean hasPrevious = previousBoundsValid;
        if (emissionBoundsUnknown || (!hasCurrent && !hasPrevious) || targetWidth <= 0 || targetHeight <= 0
                || mainWidth <= 0 || mainHeight <= 0) {
            return false;
        }
        float minX = hasCurrent ? emissionMinX : previousMinX;
        float maxX = hasCurrent ? emissionMaxX : previousMaxX;
        float minY = hasCurrent ? emissionMinY : previousMinY;
        float maxY = hasCurrent ? emissionMaxY : previousMaxY;
        if (hasCurrent && hasPrevious) {
            minX = Math.min(minX, previousMinX);
            maxX = Math.max(maxX, previousMaxX);
            minY = Math.min(minY, previousMinY);
            maxY = Math.max(maxY, previousMaxY);
        }
        float marginX = 2.0F * marginPixels / mainWidth;
        float marginY = 2.0F * marginPixels / mainHeight;
        minX = Math.max(-1.0F, minX - marginX);
        maxX = Math.min(1.0F, maxX + marginX);
        minY = Math.max(-1.0F, minY - marginY);
        maxY = Math.min(1.0F, maxY + marginY);
        if (maxX <= minX || maxY <= minY) {
            return false;
        }
        int x0 = (int) Math.floor((minX + 1.0F) * 0.5F * targetWidth);
        int y0 = (int) Math.floor((minY + 1.0F) * 0.5F * targetHeight);
        int x1 = (int) Math.ceil((maxX + 1.0F) * 0.5F * targetWidth);
        int y1 = (int) Math.ceil((maxY + 1.0F) * 0.5F * targetHeight);
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(targetWidth, x1);
        y1 = Math.min(targetHeight, y1);
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        out[0] = x0;
        out[1] = y0;
        out[2] = x1 - x0;
        out[3] = y1 - y0;
        return true;
    }

    /** enabled() 在一帧内的结果不会变(只受配置/光影包/资源重载影响), 缓存到下一帧开头。 */
    private static boolean enabledFrameValid;
    private static boolean enabledFrameValue;

    /** 记录最近一次设色掩码(由 GlStateManager._colorMask 的 mixin 维护), 省掉每个部件一次 GL 查询。 */
    private static boolean colorWriteRed = true, colorWriteGreen = true, colorWriteBlue = true;

    public static void setTrackedColorMask(boolean red, boolean green, boolean blue) {
        colorWriteRed = red;
        colorWriteGreen = green;
        colorWriteBlue = blue;
    }

    /** 彩色通道是否可写; 阴影阶段(关色写)不参与 PBR 路由。 */
    public static boolean colorWritesEnabled() {
        return colorWriteRed || colorWriteGreen || colorWriteBlue;
    }

    /** 记录一个自发光来源。 */
    public static void markEmissionSource() {
        frameEmission = true;
    }

    /**
     * 从模型层兜底判断有没有自发光骨骼: 加速渲染会在部件 {@code compile()} 的 HEAD 直接接管并取消,
     * 部件层的发光标记可能拿不到, 这里按"模型 -> 部件 -> 父链"扫一遍补上。
     */
    public static void markModelEmissionSource(java.util.List<? extends com.tacz.guns.client.model.bedrock.BedrockPart> parts) {
        if (parts == null) {
            return;
        }
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : parts) {
            for (com.tacz.guns.client.model.bedrock.BedrockPart node = part; node != null; node = node.getParent()) {
                if (node.illuminated) {
                    frameEmission = true;
                    return;
                }
            }
        }
    }

    /** 本帧是否有自发光内容可泛。 */
    public static boolean hasEmissionSource() {
        return frameEmission;
    }

    private static int materialFallbackSuppression;

    public static void pushMaterialFallbackSuppression() {
        materialFallbackSuppression++;
    }

    public static void popMaterialFallbackSuppression() {
        if (materialFallbackSuppression > 0) {
            materialFallbackSuppression--;
        }
    }

    /** 镜片/分划/目镜之类"镜内可见"的部件: 渲染期间禁用动态迷彩(否则开镜后镜内会看到迷彩与光晕)。 */
    private static int camoSuppression;

    public static void pushCamoSuppression() {
        camoSuppression++;
    }

    public static void popCamoSuppression() {
        if (camoSuppression > 0) camoSuppression--;
    }

    public static void updateCelestialLight(float partialTick) {
        // 光影启用时PBR 已被强制关闭, 不再更新 PBR uniform
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
            decayLocalLights(origin, dt);
        }
        applyLocalLightUniforms();
        setUniform(uTime, (level.getGameTime() + partialTick) / 20f);
        setUniform(uEnvStrength, Config.PBR_ENV_STRENGTH.get().floatValue());
        setUniform(uAnisotropy, Config.PBR_ANISOTROPY.get().floatValue());
        setUniform(uMetalnessBoost, Config.PBR_METALNESS_BOOST.get().floatValue());
        setUniform(uRoughnessBoost, Config.PBR_ROUGHNESS_BOOST.get().floatValue());
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
        // 天空可见度本来就做了指数平滑, 射线 ~10Hz 重算 + 移动超过 1 格才重算, 省掉每帧一次 96 格拾取。
        long skyNow = System.currentTimeMillis();
        if (skyNow - skyVisibilityCheckedAt >= 100L || skyVisibilityOrigin.distanceToSqr(origin) > 1.0D
                || skyVisibilityLevel != level) {
            skyVisibilityCheckedAt = skyNow;
            skyVisibilityOrigin = origin;
            skyVisibilityLevel = level;
            var end = origin.add(new Vec3(light.x(), light.y(), 0).scale(96));
            var cameraEntity = camera.getEntity();
            skyVisibilityResult = cameraEntity == null
                    || level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, cameraEntity)).getType() == HitResult.Type.MISS;
        }
        setUniform(uCelestialVisibility, updateCelestialVisibility(skyVisibilityResult ? 1f : 0f, dt));
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
    private static float cloudOcclusion(net.minecraft.client.multiplayer.ClientLevel level, Vec3 origin, CelestialLight light, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.getCloudsType() == net.minecraft.client.CloudStatus.OFF) {
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
    /** 天空可见度射线节流状态(见 updateCelestialLight)。 */
    private static long skyVisibilityCheckedAt;
    private static Vec3 skyVisibilityOrigin = Vec3.ZERO;
    private static net.minecraft.world.level.Level skyVisibilityLevel;
    private static boolean skyVisibilityResult = true;
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

    private static final float[] lightDirX = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightDirY = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightDirZ = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightColorR = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightColorG = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightColorB = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightStrength = new float[LOCAL_LIGHT_SLOTS];
    private static final float[] lightTargetStrength = new float[LOCAL_LIGHT_SLOTS];
    private static final Vec3[] lightSlotPos = new Vec3[LOCAL_LIGHT_SLOTS];
    private static final java.util.List<LightCandidate> LIGHT_CANDIDATES = new java.util.ArrayList<>();
    private static long localLightNextScan = 0L;
    private static Vec3 lastLocalLightScanOrigin = Vec3.ZERO;
    private static net.minecraft.world.level.Level lastLocalLightScanLevel;
    private static int lastLocalLightBlockLight = -1;
    private static long lastPbrDrawAt = 0L;

    private record LightCandidate(double score, BlockPos block, Vec3 position,
                                  net.minecraft.world.level.block.state.BlockState state) {
    }

    /** 客户端方块更新(放/拆光源、放/拆遮挡物)时调用: 变化落在扫描范围内就立刻重扫。 */
    public static void invalidateLocalLights(BlockPos pos) {
        if (pos == null) {
            return;
        }
        Vec3 origin = lastLocalLightScanOrigin;
        if (Math.abs(pos.getX() - origin.x) <= 7.0D
                && Math.abs(pos.getY() - origin.y) <= 5.0D
                && Math.abs(pos.getZ() - origin.z) <= 7.0D) {
            localLightNextScan = 0L;
        }
    }

    private static void updateLocalLights(net.minecraft.world.level.Level level, Vec3 origin, float dt) {
        long now = System.currentTimeMillis();
        BlockPos center = BlockPos.containing(origin);
        int blockLight = level.getBrightness(LightLayer.BLOCK, center);
        // 附近只要有可见光源, 玩家所在格几乎一定有方块光; 方块光为 0 时不可能有可见的方块光源, 直接跳过扫描。
        // 重扫触发: 玩家移动超过 1.5 格 / 光照等级变化(放拆光源) / 超过 1s 兜底。
        boolean moved = lastLocalLightScanOrigin.distanceToSqr(origin) > 1.0D;
        boolean changed = blockLight != lastLocalLightBlockLight || lastLocalLightScanLevel != level;
        if (!moved && !changed && now < localLightNextScan) {
            updateLocalLightSlots(origin, dt);
            return;
        }
        localLightNextScan = now + 1000L;
        lastLocalLightScanOrigin = origin;
        lastLocalLightScanLevel = level;
        lastLocalLightBlockLight = blockLight;
        LIGHT_CANDIDATES.clear();
        int count = 0;
        if (blockLight > 0) {
            Minecraft minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            Vec3 visibilityAnchor = player != null ? player.getEyePosition(1.0F) : origin;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 4, 6))) {
                var state = level.getBlockState(pos);
                int emission = state.getLightEmission(level, pos);
                if (emission <= 0) continue;
                BlockPos immutable = pos.immutable();
                double dx = immutable.getX() + 0.5D - origin.x;
                double dy = immutable.getY() + 0.5D - origin.y;
                double dz = immutable.getZ() + 0.5D - origin.z;
                double distSqr = dx * dx + dy * dy + dz * dz;
                double score = emission / (1.0D + distSqr * 0.35D);
                LIGHT_CANDIDATES.add(new LightCandidate(score, immutable, Vec3.atCenterOf(immutable), state));
            }
            // Stable sorting preserves the existing tie-breaker for equally ranked lights.
            LIGHT_CANDIDATES.sort((a, b) -> Double.compare(b.score(), a.score()));
            for (int i = 0; i < LIGHT_CANDIDATES.size() && count < LOCAL_LIGHT_SLOTS; i++) {
                LightCandidate candidate = LIGHT_CANDIDATES.get(i);
                if (isLightVisible(level, visibilityAnchor, candidate.block(), player)) {
                    LIGHT_CANDIDATES.set(count++, candidate);
                }
            }
        }
        // 开火/镭射/爆炸等动态光只照亮世界, 不参与枪身反光:
            // 枪口闪光离枪太近, 参与反光会变成逐帧闪烁的假高光。
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
                float target = (float) Math.min(1.0D, candidate.score() / 7.0D);
                if (previous == null || previous.distanceToSqr(candidate.position()) > 6.25D) {
                    lightStrength[slot] = Math.min(lightStrength[slot], target);
                }
                int color = candidate.state().getMapColor(level, candidate.block()).col;
                float red = ((color >> 16) & 255) / 255f;
                float green = ((color >> 8) & 255) / 255f;
                float blue = (color & 255) / 255f;
                if (red + green + blue < 0.1f) {
                    red = 1.0f;
                    green = 0.86f;
                    blue = 0.67f;
                }
                lightSlotPos[slot] = candidate.position();
                lightColorR[slot] = red;
                lightColorG[slot] = green;
                lightColorB[slot] = blue;
                lightTargetStrength[slot] = target;
            }
        updateLocalLightSlots(origin, dt);
    }

    private static void decayLocalLights(Vec3 origin, float dt) {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            lightTargetStrength[slot] = 0f;
        }
        updateLocalLightSlots(origin, dt);
    }

    private static void updateLocalLightSlots(Vec3 origin, float dt) {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            Vec3 pos = lightSlotPos[slot];
            if (pos == null) {
                lightStrength[slot] = approach(lightStrength[slot], 0f, dt);
                if (lightStrength[slot] < 0.001f) {
                    lightStrength[slot] = 0f;
                }
                continue;
            }
            double dx = pos.x - origin.x;
            double dy = pos.y - origin.y;
            double dz = pos.z - origin.z;
            double length = Math.max(1.0E-4D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            lightDirX[slot] = (float) (dx / length);
            lightDirY[slot] = (float) (dy / length);
            lightDirZ[slot] = (float) (dz / length);
            lightStrength[slot] = approach(lightStrength[slot], lightTargetStrength[slot], dt);
        }
    }

    private static void applyLocalLightUniforms() {
        for (int slot = 0; slot < LOCAL_LIGHT_SLOTS; slot++) {
            if (lightSlotPos[slot] != null) {
                setUniform(uLocalLightDir[slot], (float) lightSlotPos[slot].x, (float) lightSlotPos[slot].y, (float) lightSlotPos[slot].z);
            } else {
                setUniform(uLocalLightDir[slot], 0f, 0f, 0f);
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
    public static boolean enabled() {
        if (enabledFrameValid) {
            return enabledFrameValue;
        }
        boolean value = computeEnabled();
        enabledFrameValue = value;
        enabledFrameValid = true;
        return value;
    }

    private static boolean computeEnabled() {
        if (shader == null || !Config.PBR_ENABLED.get()
                || com.ssscript.taczfixes.client.util.IrisShaderCompat.isShaderPackInUse()) return false;
        // TaCZ's AR compatibility entry point selects conventional gun rendering
        // while this shader is active. Do not query it here: that would recurse.
        return true;
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        MATERIALS.clear();
        TYPES.clear();
        CAMO_TYPES.clear();
        com.ssscript.taczfixes.client.util.camo.DynamicCamo.clear();
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
        shader = blurShader = compositeShader = maskShader = smoothShader = null;
        sceneCaptureShader = null;
        clearUniformCache();
        enabledFrameValid = false;
        // 着色器重载后需要重新验证加速路径的发光写入。
        acceleratedEmissionVerified = false;
        acceleratedEmissionVerifyAttempts = 0;
        lastResolveOriginal = null;
        lastResolveResult = null;
        reloadCloudTexture();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    // 动态迷彩: 把枪包提供的片段拼进 gun_pbr, 生成带 CamoIndex 分发的程序;
                    // 没有迷彩(或编译失败)时退回原程序, 迷彩行在面板里自然消失。
                    try {
                        com.ssscript.taczfixes.client.util.camo.CamoShaderBuilder.Result camo =
                                com.ssscript.taczfixes.client.util.camo.CamoShaderBuilder.build(event.getResourceProvider());
                        com.ssscript.taczfixes.client.util.camo.DynamicCamo.publish(camo);
                        if (camo != null) {
                            camoShader = camo.shader();
                            shader = camoShader;
                        } else {
                            shader = s;
                        }
                    } catch (Throwable t) {
                        TaczFixesMod.LOGGER.error("taczfixes: dynamic camo build failed, falling back to base gun_pbr", t);
                        com.ssscript.taczfixes.client.util.camo.DynamicCamo.publish(null);
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
                    uHasSeparateBloomDepth = s.safeGetUniform("HasSeparateDepth");
                    uCompositeScissorRect = s.safeGetUniform("ScissorRect");
                    uCompositeScissorFade = s.safeGetUniform("ScissorFade");
                    uCompositeHasScissor = s.safeGetUniform("HasScissor");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_bloom_smooth"), DefaultVertexFormat.POSITION_TEX), s -> {
                    smoothShader = s;
                    uSmoothAlpha = s.safeGetUniform("Alpha");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "pbr_scene_capture"), DefaultVertexFormat.POSITION_TEX),
                s -> sceneCaptureShader = s);
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = null;
        uReflectionStrength = uGlareStrength = uEmissionStrength = null;
        uGlareDepthTest = uFallbackMaterial = null;
        uCelestialDirection = uCelestialColor = uSkyAmbient = null;
        uCelestialVisibility = uLocalSkyExposure = uWetness = uWetSurface = null;
        uSkyZenith = uSkyHorizon = uSunHorizon = uSunAzimuth = uSunOcclusion = null;
        uDefaultRoughness = uDefaultMetal = uDefaultReflection = uEnvStrength = null;
        uAnisotropy = uWetDetail = uSSRStrength = uTime = null;
        uMetalnessBoost = uRoughnessBoost = null;
        uSSRSteps = uScreenSize = null;
        uViewRotX = uViewRotY = uViewRotZ = null;
        uCameraPos = null;
        uCamoIndex = uCamoStrength = uCamoEmissive = null;
        uBlurDirection = uCompositeStrength = uWideStrength = uHasScopeMask = null;
        uHasSeparateBloomDepth = null;
        uCompositeScissorRect = uCompositeScissorFade = uCompositeHasScissor = null;
        uSmoothAlpha = null;
        for (int i = 0; i < LOCAL_LIGHT_SLOTS; i++) {
            uLocalLightDir[i] = null;
            uLocalLightColor[i] = null;
            uLocalLightStrength[i] = null;
        }
    }

    private static void cacheGunUniforms(ShaderInstance s) {
        uHasSpecular = s.safeGetUniform("HasSpecular");
        uHasNormal = s.safeGetUniform("HasNormal");
        uEmissionPass = s.safeGetUniform("EmissionPass");
        uReflectionStrength = s.safeGetUniform("ReflectionStrength");
        uGlareStrength = s.safeGetUniform("GlareStrength");
        uGlareDepthTest = s.safeGetUniform("GlareDepthTest");
        uEmissionStrength = s.safeGetUniform("EmissionStrength");
        uFallbackMaterial = s.safeGetUniform("FallbackMaterial");
        for (int i = 0; i < LOCAL_LIGHT_SLOTS; i++) {
            int slot = i + 1;
            uLocalLightDir[i] = s.safeGetUniform("LocalLightPos" + slot);
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
        uMetalnessBoost = s.safeGetUniform("MetalnessBoost");
        uRoughnessBoost = s.safeGetUniform("RoughnessBoost");
        uWetDetail = s.safeGetUniform("WetDetail");
        uSSRStrength = s.safeGetUniform("SSRStrength");
        uSSRSteps = s.safeGetUniform("SSRSteps");
        uScreenSize = s.safeGetUniform("ScreenSize");
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

    static void setUniform(AbstractUniform uniform, org.joml.Matrix4f value) {
        if (uniform != null) uniform.set(value);
    }

    /** 与材质无关的全局 uniform: 兜底材质参数、天空环境强度、湿润参数与屏幕尺寸。*/
    private static void applyGlobalUniforms(boolean hand, boolean rainExposed) {
        setUniform(uFallbackMaterial, Config.PBR_FALLBACK_MATERIAL.get() ? 1 : 0);
        setUniform(uDefaultRoughness, Config.PBR_DEFAULT_ROUGHNESS.get().floatValue());
        setUniform(uDefaultMetal, Config.PBR_DEFAULT_METAL.get().floatValue());
        setUniform(uDefaultReflection, Config.PBR_DEFAULT_REFLECTION.get().floatValue());
        setUniform(uAnisotropy, Config.PBR_ANISOTROPY.get().floatValue());
        setUniform(uMetalnessBoost, Config.PBR_METALNESS_BOOST.get().floatValue());
        setUniform(uRoughnessBoost, Config.PBR_ROUGHNESS_BOOST.get().floatValue());
        applyWetUniforms(hand, rainExposed);
        var main = Minecraft.getInstance().getMainRenderTarget();
        boolean ssr = sceneColorCaptured && sceneColorTexture != 0 && sceneDepthTexture != 0
                && Config.PBR_SSR.get() && Config.PBR_SSR_STRENGTH.get() > 0.0D;
        setUniform(uSSRStrength, ssr ? Config.PBR_SSR_STRENGTH.get().floatValue() : 0f);
        setUniform(uSSRSteps, ssr ? Config.PBR_SSR_STEPS.get() : 0);
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

    static void setSmoothAlpha(float value) {
        if (uSmoothAlpha != null) uSmoothAlpha.set(value);
    }

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    static void setSeparateBloomDepth(boolean separate) {
        setUniform(uHasSeparateBloomDepth, separate ? 1 : 0);
    }

    /** 合成 pass 的裁剪矩形(x0,y0,x1,y1 像素)与边缘淡出宽度。 */
    static void setCompositeScissor(boolean enabled, int x0, int y0, int x1, int y1, float fade) {
        if (uCompositeHasScissor != null) uCompositeHasScissor.set(enabled ? 1 : 0);
        if (!enabled) return;
        if (uCompositeScissorRect != null) uCompositeScissorRect.set((float) x0, (float) y0, (float) x1, (float) y1);
        if (uCompositeScissorFade != null) uCompositeScissorFade.set(fade);
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
        RenderSystem.setShader(() -> shader);
        bindGlareDepth();
    }

    private static void bindGlareDepth() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        int fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean mainTarget = fbo == main.frameBufferId;
        if (mainTarget) {
            RenderSystem.setShaderTexture(5, main.getDepthTextureId());
            setUniform(uScreenSize, (float) main.width, (float) main.height);
        }
        setUniform(uGlareDepthTest, mainTarget ? 1 : 0);
        lastDrawFramebuffer = fbo;
        lastDrawFramebufferKnown = true;
    }

    /** bindGlareDepth() 顺手记下的当前绘制 FBO, 供 draw 时复用, 省一次 GL 查询。 */
    private static int lastDrawFramebuffer = -1;
    private static boolean lastDrawFramebufferKnown;

    private static int sceneCaptureFbo = -1;
    private static boolean sceneCaptureUnavailable;
    private static VertexBuffer sceneCaptureQuad;
    private static ShaderInstance sceneCaptureShader;
    private static final int[] SCENE_CAPTURE_DRAW_BUFFERS = {
            GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1};
    private static final org.joml.Matrix4f SCENE_CAPTURE_IDENTITY = new org.joml.Matrix4f();

    /**
     * 场景渲染结束(手部之前)把主目标的颜色与深度按半分辨率捕获到独立纹理, 供屏幕空间反射采样。
     * 用一次半分辨率全屏 pass 采样主目标的颜色/深度纹理完成(渲染到自己的 FBO, 不构成反馈回路),
     * 深度按 16 位打包进 RG 通道: 比两次全屏拷贝便宜, 也不依赖驱动是否支持"缩放 blit 深度格式"。
     */
    public static void onRenderLevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        sceneColorCaptured = false;
        if (!Config.PBR_SSR.get() || Config.PBR_SSR_STRENGTH.get() <= 0.0D
                || shader == null || sceneCaptureShader == null || !enabled()) {
            return;
        }
        // 最近没有 PBR 几何在画(手里不是枪/没画世界里的枪)时, 不需要为 SSR 捕获场景。
        if (System.currentTimeMillis() - lastPbrDrawAt > 500L) {
            return;
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }
        if (!ensureSceneCaptureTargets(main)) {
            return;
        }
        int halfW = sceneColorWidth;
        int halfH = sceneColorHeight;
        ensureSceneCaptureQuad();
        ShaderInstance previous = RenderSystem.getShader();
        int colorSlot = RenderSystem.getShaderTexture(0);
        int depthSlot = RenderSystem.getShaderTexture(1);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneCaptureFbo);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.viewport(0, 0, halfW, halfH);
            RenderSystem.setShaderTexture(0, main.getColorTextureId());
            RenderSystem.setShaderTexture(1, main.getDepthTextureId());
            RenderSystem.setShader(() -> sceneCaptureShader);
            sceneCaptureQuad.bind();
            sceneCaptureQuad.drawWithShader(SCENE_CAPTURE_IDENTITY, SCENE_CAPTURE_IDENTITY, sceneCaptureShader);
        } catch (Throwable ignored) {
        } finally {
            VertexBuffer.unbind();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousDraw);
            RenderSystem.setShaderTexture(0, colorSlot);
            RenderSystem.setShaderTexture(1, depthSlot);
            RenderSystem.setShader(() -> previous);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            main.bindWrite(true);
        }
        sceneColorCaptured = true;
    }

    /** 半分辨率捕获目标: 颜色 LINEAR、打包深度 NEAREST; 两张 RGBA8 做 MRT, 一次 pass 同时写。 */
    private static boolean ensureSceneCaptureTargets(RenderTarget main) {
        if (sceneCaptureUnavailable) {
            return false;
        }
        int halfW = Math.max(1, main.width / 2);
        int halfH = Math.max(1, main.height / 2);
        if (sceneCaptureFbo >= 0 && sceneColorWidth == halfW && sceneColorHeight == halfH
                && sceneDepthWidth == halfW && sceneDepthHeight == halfH) {
            return true;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (sceneColorTexture == 0) {
                sceneColorTexture = com.mojang.blaze3d.platform.GlStateManager._genTexture();
            }
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(sceneColorTexture);
            if (sceneColorWidth != halfW || sceneColorHeight != halfH) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, halfW, halfH, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                sceneColorWidth = halfW;
                sceneColorHeight = halfH;
            }
            if (sceneDepthTexture == 0) {
                sceneDepthTexture = com.mojang.blaze3d.platform.GlStateManager._genTexture();
            }
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(sceneDepthTexture);
            if (sceneDepthWidth != halfW || sceneDepthHeight != halfH) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, halfW, halfH, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                sceneDepthWidth = halfW;
                sceneDepthHeight = halfH;
            }
            return ensureSceneCaptureFbo();
        } catch (Throwable throwable) {
            sceneCaptureUnavailable = true;
            return false;
        } finally {
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(previousTexture);
        }
    }

    private static boolean ensureSceneCaptureFbo() {
        if (sceneCaptureFbo >= 0) {
            return true;
        }
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, sceneColorTexture, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL11.GL_TEXTURE_2D, sceneDepthTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(fbo);
            sceneCaptureUnavailable = true;
            return false;
        }
        GL30.glDrawBuffers(SCENE_CAPTURE_DRAW_BUFFERS);
        sceneCaptureFbo = fbo;
        return true;
    }

    private static void ensureSceneCaptureQuad() {
        if (sceneCaptureQuad != null) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-1, -1, 0).setUv(0, 0);
        builder.addVertex(1, -1, 0).setUv(1, 0);
        builder.addVertex(1, 1, 0).setUv(1, 1);
        builder.addVertex(-1, 1, 0).setUv(0, 1);
        sceneCaptureQuad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        sceneCaptureQuad.bind();
        sceneCaptureQuad.upload(builder.buildOrThrow());
    }

    private static void releaseSceneColor() {
        if (sceneCaptureFbo >= 0) {
            GL30.glDeleteFramebuffers(sceneCaptureFbo);
            sceneCaptureFbo = -1;
        }
        if (sceneCaptureQuad != null) {
            sceneCaptureQuad.close();
            sceneCaptureQuad = null;
        }
        sceneCaptureUnavailable = false;
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
        boolean hand = handGeometry;
        boolean rainExposed = currentWorldRainExposed();
        // 绝大多数几何提交是同一个 RenderType 连续解析: 命中上一次的结果就不用再查表/建 key。
        // 动态迷彩/材质兜底抑制会改变结果, 这两类情况不走快路径。
        if (camoShader == null && camoSuppression == 0 && materialFallbackSuppression == 0) {
            if (lastResolveOriginal == original && lastResolveResult != null
                    && lastResolveHand == hand && lastResolveRain == rainExposed) {
                return lastResolveResult;
            }
            RenderType resolved = TYPES.computeIfAbsent(new TypeKey(original, hand, rainExposed),
                    k -> resolveType(k.original(), composite, true, CamoUniforms.NONE,
                            k.hand(), k.rainExposed()));
            lastResolveOriginal = original;
            lastResolveResult = resolved;
            lastResolveHand = hand;
            lastResolveRain = rainExposed;
            return resolved;
        }
        CamoUniforms camo = camoSuppression > 0 ? CamoUniforms.NONE : camoForCurrentItem();
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

    private static RenderType lastResolveOriginal;
    private static RenderType lastResolveResult;
    private static boolean lastResolveHand;
    private static boolean lastResolveRain;

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
        com.ssscript.taczfixes.client.util.camo.DynamicCamo.Entry entry =
                com.ssscript.taczfixes.client.util.camo.DynamicCamo.appliedOn(camoOwner);
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
        /** 本次加速绘制里是否开着发光 MRT(在 clearRenderState() 里恢复)。 */
        private boolean acceleratedEmissionWrite;
        /** 加速绘制前保存的纹理槽 3/4/5/6/7: 主绘制绑定了枪械贴图/主深度, 结束后必须还回去。 */
        private boolean texturesSaved;
        private int savedSpecularSlot, savedNormalSlot, savedDepthSlot, savedSceneSlot, savedSceneDepthSlot;

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
            // 加速渲染时 PbrType.draw() 不会执行(几何由 AR 自己提交), 这里也记一次时间:
            // SSR 场景捕获与局部光更新都靠它判断"最近有没有 PBR 几何在画"。
            lastPbrDrawAt = System.currentTimeMillis();
            if (isAcceleratedPbrPass()) {
                // 主绘制会绑定枪械贴图(3/4)、主深度(5)、场景色/深度(6/7): 先记下旧值, clearRenderState() 里还回去。
                savedSpecularSlot = RenderSystem.getShaderTexture(3);
                savedNormalSlot = RenderSystem.getShaderTexture(4);
                savedDepthSlot = RenderSystem.getShaderTexture(5);
                savedSceneSlot = RenderSystem.getShaderTexture(6);
                savedSceneDepthSlot = RenderSystem.getShaderTexture(7);
                texturesSaved = true;
                // applyMaterialState() 内部会 bindGlareDepth(), 不需要在这之前再查一次绘制 FBO。
                applyMaterialState(material, hand, rainExposed);
                applyCamoUniforms(camo);
                // AR 的 drawBuffers 会在 setupRenderState() 之后紧接着 drawElements():
                // 这里把发光 MRT 打开, 主绘制一次就同时写出泛光光源, 不用再补画整遍几何。
                acceleratedEmissionWrite = false;
                if (material.emissive() || camo.emissive() > 0f) {
                    markEmissionSource();
                }
                if (PbrBloom.canCapture()) {
                    int fbo = lastDrawFramebufferKnown ? lastDrawFramebuffer
                            : GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                    boolean mainTarget = fbo == Minecraft.getInstance().getMainRenderTarget().frameBufferId;
                    acceleratedEmissionWrite = PbrBloom.beginEmissionWrite(mainTarget);
                    if (acceleratedEmissionWrite) {
                        emissionBoundsUnknown = true;
                        acceleratedEmissionWrites++;
                    }
                }
            } else {
                bindGlareDepth();
            }
        }

        /** AR 绘制结束后(或普通路径 draw() 收尾时)恢复单颜色附件与纹理槽。 */
        @Override
        public void clearRenderState() {
            if (acceleratedEmissionWrite) {
                acceleratedEmissionWrite = false;
                PbrBloom.endEmissionWrite();
            }
            if (texturesSaved) {
                texturesSaved = false;
                RenderSystem.setShaderTexture(3, savedSpecularSlot);
                RenderSystem.setShaderTexture(4, savedNormalSlot);
                RenderSystem.setShaderTexture(5, savedDepthSlot);
                RenderSystem.setShaderTexture(6, savedSceneSlot);
                RenderSystem.setShaderTexture(7, savedSceneDepthSlot);
            }
            original.clearRenderState();
        }

        @Override
        public void draw(MeshData data) {
            if (data == null) return;
            if (mesh == null) mesh = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            // 纹理槽的"旧值"必须在 setupRenderState() 之前读: 它会把 slot5 绑成主深度。
            int mainFbo = Minecraft.getInstance().getMainRenderTarget().frameBufferId;
            int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean mainTarget = previousFbo == mainFbo;
            boolean ssrActive = sceneColorCaptured && Config.PBR_SSR.get()
                    && Config.PBR_SSR_STRENGTH.get() > 0.0D;
            // 只保存本 draw 真正会改动的纹理槽位。
            int specularSlot = material.specular() != null ? RenderSystem.getShaderTexture(3) : 0;
            int normalSlot = material.normal() != null ? RenderSystem.getShaderTexture(4) : 0;
            int depthSlot = mainTarget ? RenderSystem.getShaderTexture(5) : 0;
            int sceneSlot = ssrActive ? RenderSystem.getShaderTexture(6) : 0;
            int sceneDepthSlot = ssrActive ? RenderSystem.getShaderTexture(7) : 0;
            setupRenderState();
            ShaderInstance previous = RenderSystem.getShader();
            try {
                // Read vertices before upload and use the matrices installed by setupRenderState().
                boolean allEmitted = material.emissive() || camo.emissive() > 0f;
                boolean geometryEmitted = analyzeEmissionGeometry(data, allEmitted);
                if (geometryEmitted) {
                    markEmissionSource();
                }
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
                if (!emissionReplay) {
                    // MRT: 主绘制同一遍着色同时把发光写进主目标的第二颜色附件, 不再单独重画一遍几何。
                    boolean emissionWrite = geometryEmitted
                            && PbrBloom.beginEmissionWrite(mainTarget);
                    try {
                        mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                    } finally {
                        if (emissionWrite) {
                            PbrBloom.endEmissionWrite();
                        }
                    }
                    if (!emissionWrite && geometryEmitted && mainTarget
                            && PbrBloom.mrtUnavailable() && PbrBloom.beginFallbackCapture()) {
                        try {
                            drawEmissionFallback(shader);
                        } finally {
                            com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                        }
                    }
                } else if (geometryEmitted && mainTarget && PbrBloom.beginFallbackCapture()) {
                    // 加速渲染时主绘制被跳过, 这里补画一次发光(只写发光目标, 不碰主画面)。
                    try {
                        drawEmissionFallback(shader);
                    } finally {
                        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                    }
                }            } finally {
                VertexBuffer.unbind();
                if (material.specular() != null) RenderSystem.setShaderTexture(3, specularSlot);
                if (material.normal() != null) RenderSystem.setShaderTexture(4, normalSlot);
                if (mainTarget) RenderSystem.setShaderTexture(5, depthSlot);
                if (ssrActive) {
                    RenderSystem.setShaderTexture(6, sceneSlot);
                    RenderSystem.setShaderTexture(7, sceneDepthSlot);
                }
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
                mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            } finally {
                setUniform(uEmissionPass, 0);
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
