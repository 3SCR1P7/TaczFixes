package com.ssscript.taczfixes.client.render;
import com.ssscript.taczfixes.client.mixin.pbr.PbrCompositeAccessor;
import com.ssscript.taczfixes.client.mixin.pbr.PbrStateAccessor;
import com.ssscript.taczfixes.client.mixin.pbr.PbrTextureAccessor;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import com.ssscript.taczfixes.TaczFixesMod;

import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
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
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader;
    // 缓存 Uniform 引用, 避免每帧/每次枪械绘制都按字符串查找。
    private static AbstractUniform uHasSpecular, uHasNormal, uEmissionPass, uScopeDivisionPass;
    private static AbstractUniform uReflectionStrength, uReflectionArea, uGlareStrength, uEmissionStrength;
    private static AbstractUniform uLocalLightDirection, uLocalLightColor, uLocalLightStrength;
    private static AbstractUniform uCelestialDirection, uCelestialColor, uSkyAmbient;
    private static AbstractUniform uCelestialVisibility, uLocalSkyExposure, uWetness;
    private static AbstractUniform uBlurDirection, uCompositeStrength, uHasScopeMask;
    private static VertexBuffer mesh;
    private static final int MATERIALS_MAX = 256;
    private static final Map<ResourceLocation, Optional<Material>> MATERIALS =
            new java.util.LinkedHashMap<>(MATERIALS_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Optional<Material>> eldest) {
                    return size() > MATERIALS_MAX;
                }
            };
    private static final int TYPES_MAX = 512;
    // RenderType 未覆写 equals/hashCode(默认标识语义), 用访问序 LRU 避免战斗/改装中整体清空重建。
    private static final Map<RenderType, RenderType> TYPES =
            new java.util.LinkedHashMap<>(TYPES_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RenderType, RenderType> eldest) {
                    return size() > TYPES_MAX;
                }
            };
    private static final Method OPTIFINE_SHADERS = findOptifine();

    private record Material(ResourceLocation specular, ResourceLocation normal, boolean emissive) {}

    private static int tracerBloomSuppression;

    /** 曳光弹渲染期间抑制自发光泛光捕获(是否最终生效由 Config.PBR_BLOOM_TRACER 决定)。 */
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
        if (shader == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var origin = camera.getPosition();
        // 局部光源(火把/灯笼等): 非天空维度也有
        // 只在最近实际绘制过 PBR 时扫描局部光, 避免无枪/无 PBR 时白扫方块。
        if (System.currentTimeMillis() - lastPbrDrawAt <= 500L) {
            updateLocalLight(level, origin);
        }
        setUniform(uLocalLightDirection, localLightX, localLightY, localLightZ);
        setUniform(uLocalLightColor, localLightR, localLightG, localLightB);
        setUniform(uLocalLightStrength, localLightStrength);
        if (!level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            setUniform(uCelestialDirection, 0f, 1f, 0f);
            setUniform(uCelestialColor, 0f, 0f, 0f);
            setUniform(uSkyAmbient, 0f);
            setUniform(uCelestialVisibility, 0f);
            setUniform(uLocalSkyExposure, 0f);
            setUniform(uWetness, updateWetness(0f));
            return;
        }
        var light = CelestialLight.sample(level.getTimeOfDay(partialTick), level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick), level.getMoonBrightness());
        setUniform(uCelestialDirection, light.x(), light.y(), 0f);
        setUniform(uCelestialColor, light.red(), light.green(), light.blue());
        setUniform(uSkyAmbient, light.ambient());
        // A bounded ray catches ceilings and walls in the light's actual direction.
        var end = origin.add(new Vec3(light.x(), light.y(), 0).scale(96));
        boolean visible = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, camera.getEntity())).getType() == HitResult.Type.MISS;
        setUniform(uCelestialVisibility, visible ? 1f : 0f);
        setUniform(uLocalSkyExposure, level.getBrightness(LightLayer.SKY, BlockPos.containing(origin)) / 15f);
        // 淋雨打湿: 仅主世界、露天且头顶无遮挡时生效, 强度跟随雨量。
        // 用玩家位置而不是相机位置: 第三人称相机可能在屋檐外, 但枪和玩家在遮挡下不应打湿。
        float targetWetness = 0f;
        var wetnessAnchor = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.position() : origin;
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.isRaining()) {
            BlockPos wetPos = BlockPos.containing(wetnessAnchor.x, wetnessAnchor.y + 0.2D, wetnessAnchor.z);
            // isRainingAt 已包含 canSeeSky/高度图/群系降雨检查; canSeeSky 再显式确认头顶无方块遮挡。
            if (level.canSeeSky(wetPos) && level.isRainingAt(wetPos)) {
                targetWetness = level.getRainLevel(partialTick);
            }
        }
        setUniform(uWetness, updateWetness(targetWetness));
    }

    private static float smoothedWetness = 0f;
    private static long wetnessLastNanos;
    private static final float WET_SECONDS = 5.0f;
    private static final float DRY_SECONDS = 8.0f;

    /** 淋湿/晾干都按时间渐变, 避免进出室内时瞬间跳变。 */
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
    private static float localLightX = 0f;
    private static float localLightY = 1f;
    private static float localLightZ = 0f;
    private static float localLightR = 1f;
    private static float localLightG = 1f;
    private static float localLightB = 1f;
    private static float localLightStrength = 0f;
    private static long localLightNextScan = 0L;
    private static BlockPos localLightBlock = null;
    private static int localLightEmission = 0;
    private static long lastPbrDrawAt = 0L;

    /** 扫描附近发光方块(节流); 方向/强度每帧按当前位置重算, 移动时高光平滑跟随不卡顿。 */
    private static void updateLocalLight(net.minecraft.world.level.Level level, Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        Vec3 visibilityAnchor = player != null ? player.getEyePosition(1.0F) : origin;
        long now = System.currentTimeMillis();
        if (now >= localLightNextScan) {
            localLightNextScan = now + 250L;
            BlockPos center = BlockPos.containing(origin);
            BlockPos best = null;
            int bestEmission = 0;
            double bestScore = 0.0D;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 4, 6))) {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                int emission = state.getLightEmission(level, pos);
                if (emission <= 0) continue;
                BlockPos immutable = pos.immutable();
                if (!isLightVisible(level, visibilityAnchor, immutable, player)) continue;
                double dx = pos.getX() + 0.5D - origin.x;
                double dy = pos.getY() + 0.5D - origin.y;
                double dz = pos.getZ() + 0.5D - origin.z;
                double score = emission / (1.0D + (dx * dx + dy * dy + dz * dz) * 0.35D);
                if (score > bestScore) {
                    bestScore = score;
                    best = immutable;
                    bestEmission = emission;
                }
            }
            localLightBlock = best;
            localLightEmission = bestEmission;
            if (best != null) {
                int color = level.getBlockState(best).getMapColor(level, best).col;
                int r = (color >> 16) & 255;
                int g = (color >> 8) & 255;
                int b = color & 255;
                if (r + g + b < 24) {
                    r = 255;
                    g = 220;
                    b = 170;
                }
                localLightR = r / 255f;
                localLightG = g / 255f;
                localLightB = b / 255f;
            }
        }
        float targetStrength = 0f;
        float targetX = localLightX;
        float targetY = localLightY;
        float targetZ = localLightZ;
        if (localLightBlock != null && isLightVisible(level, visibilityAnchor, localLightBlock, player)) {
            double dx = localLightBlock.getX() + 0.5D - origin.x;
            double dy = localLightBlock.getY() + 0.5D - origin.y;
            double dz = localLightBlock.getZ() + 0.5D - origin.z;
            double len = Math.max(1.0E-4D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            targetX = (float) (dx / len);
            targetY = (float) (dy / len);
            targetZ = (float) (dz / len);
            double score = localLightEmission / (1.0D + len * len * 0.35D);
            targetStrength = (float) Math.max(0.0D, Math.min(1.0D, score / 7.0D));
        }
        // 方向每帧更新(连续), 强度轻微平滑避免切换光源跳变
        localLightX = targetX;
        localLightY = targetY;
        localLightZ = targetZ;
        localLightStrength += (targetStrength - localLightStrength) * 0.35f;
    }

    /** 光源到玩家眼睛之间是否有遮挡方块; 玻璃等不遮挡的方块会穿过。 */
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
        if (shader == null || !Config.PBR_ENABLED.get() || OculusCompat.isUsingRenderPack()) return false;
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
        shader = blurShader = compositeShader = maskShader = null;
        clearUniformCache();
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> {
                    shader = s;
                    cacheGunUniforms(s);
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_mask"), DefaultVertexFormat.POSITION_TEX), s -> maskShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_blur"), DefaultVertexFormat.POSITION_TEX), s -> {
                    blurShader = s;
                    uBlurDirection = s.safeGetUniform("Direction");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_composite"), DefaultVertexFormat.POSITION_TEX), s -> {
                    compositeShader = s;
                    uCompositeStrength = s.safeGetUniform("Strength");
                    uHasScopeMask = s.safeGetUniform("HasScopeMask");
                });
    }

    private static void clearUniformCache() {
        uHasSpecular = uHasNormal = uEmissionPass = uScopeDivisionPass = null;
        uReflectionStrength = uReflectionArea = uGlareStrength = uEmissionStrength = null;
        uLocalLightDirection = uLocalLightColor = uLocalLightStrength = null;
        uCelestialDirection = uCelestialColor = uSkyAmbient = null;
        uCelestialVisibility = uLocalSkyExposure = uWetness = null;
        uBlurDirection = uCompositeStrength = uHasScopeMask = null;
    }

    private static void cacheGunUniforms(ShaderInstance s) {
        uHasSpecular = s.safeGetUniform("HasSpecular");
        uHasNormal = s.safeGetUniform("HasNormal");
        uEmissionPass = s.safeGetUniform("EmissionPass");
        uScopeDivisionPass = s.safeGetUniform("ScopeDivisionPass");
        uReflectionStrength = s.safeGetUniform("ReflectionStrength");
        uReflectionArea = s.safeGetUniform("ReflectionArea");
        uGlareStrength = s.safeGetUniform("GlareStrength");
        uEmissionStrength = s.safeGetUniform("EmissionStrength");
        uLocalLightDirection = s.safeGetUniform("LocalLightDirection");
        uLocalLightColor = s.safeGetUniform("LocalLightColor");
        uLocalLightStrength = s.safeGetUniform("LocalLightStrength");
        uCelestialDirection = s.safeGetUniform("CelestialDirection");
        uCelestialColor = s.safeGetUniform("CelestialColor");
        uSkyAmbient = s.safeGetUniform("SkyAmbient");
        uCelestialVisibility = s.safeGetUniform("CelestialVisibility");
        uLocalSkyExposure = s.safeGetUniform("LocalSkyExposure");
        uWetness = s.safeGetUniform("Wetness");
    }

    /** 缓存为空(资源重载/着色器缺失)时跳过写入。 */
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

    /** 共享模糊着色器的方向(UV 单位)。 */
    static void setBlurDirection(float x, float y) {
        if (uBlurDirection != null) uBlurDirection.set(x, y);
    }

    /** 共享泛光合成着色器的强度。 */
    static void setBloomStrength(float value) {
        if (uCompositeStrength != null) uCompositeStrength.set(value);
    }

    static void setHasScopeMask(int value) {
        if (uHasScopeMask != null) uHasScopeMask.set(value);
    }

    public static RenderType resolve(RenderType original) {
        if (!enabled() || original instanceof PbrType || original.format() != DefaultVertexFormat.NEW_ENTITY
                || !(original instanceof PbrCompositeAccessor composite)) return original;
        return TYPES.computeIfAbsent(original, key -> {
            var textureState = ((PbrStateAccessor) (Object) composite.taczfixes$pbrState()).taczfixes$pbrTexture();
            var texture = ((PbrTextureAccessor) textureState).taczfixes$pbrTextureLocation();
            if (texture.isEmpty()) return original;
            ResourceLocation base = com.ssscript.taczfixes.client.util.GunRecolorManager.sourceOf(texture.get());
            Material material = material(base);
            if (material == null || material.specular() == null && material.normal() == null) {
                ResourceLocation own = com.ssscript.taczfixes.client.util.GunRecolorManager.currentBaseTexture();
                if (own != null && !own.equals(base)) {
                    Material ownMaterial = material(own);
                    if (ownMaterial != null && (ownMaterial.specular() != null || ownMaterial.normal() != null)) {
                        material = ownMaterial;
                    }
                }
            }
            return material == null ? original : new PbrType(original, material);
        });
    }

    private static Material material(ResourceLocation base) {
        return MATERIALS.computeIfAbsent(base, PbrRenderer::findMaterial).orElse(null);
    }

    private static Optional<Material> findMaterial(ResourceLocation base) {
        String path = base.getPath();
        if (!path.endsWith(".png")) return Optional.empty();
        String stem = path.substring(0, path.length() - 4);
        ResourceLocation specular = new ResourceLocation(base.getNamespace(), stem + "_s.png");
        ResourceLocation normal = new ResourceLocation(base.getNamespace(), stem + "_n.png");
        var resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation specularLocation = resources.getResource(specular).isPresent() ? specular : null;
        boolean emissive = specularLocation != null && hasEmissiveAlpha(resources, specularLocation);
        return Optional.of(new Material(specularLocation,
                resources.getResource(normal).isPresent() ? normal : null, emissive));
    }

    /** LabPBR alpha 255 为非自发光哨兵值、0 为无自发光, 其余 alpha 代表发光强度。 */
    private static boolean hasEmissiveAlpha(ResourceManager resources, ResourceLocation location) {
        var resource = resources.getResource(location);
        if (resource.isEmpty()) return false;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (alpha != 0 && alpha != 255) return true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return false;
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
                setUniform(uHasSpecular, material.specular() == null ? 0 : 1);
                setUniform(uHasNormal, material.normal() == null ? 0 : 1);
                setUniform(uReflectionStrength, material.specular() == null ? 0f : Config.PBR_REFLECTION.get().floatValue());
                setUniform(uReflectionArea, Config.PBR_REFLECTION_AREA.get().floatValue());
                setUniform(uGlareStrength, material.specular() == null ? 0f : Config.PBR_GLARE_STRENGTH.get().floatValue());
                setUniform(uEmissionStrength, Config.PBR_EMISSION.get().floatValue());
                setUniform(uEmissionPass, 0);
                RenderSystem.setShader(() -> shader);
                mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
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
                RenderSystem.setShader(() -> previous);
                clearRenderState();
            }
        }
    }

    private PbrRenderer() {}
}