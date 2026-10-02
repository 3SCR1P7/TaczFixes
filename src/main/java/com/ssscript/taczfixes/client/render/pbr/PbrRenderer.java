package com.ssscript.taczfixes.client.render.pbr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.ssscript.taczfixes.common.config.Config;
import com.mojang.blaze3d.vertex.*;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.mixin.*;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.compat.optifine.OptifineCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PbrRenderer {
    static ShaderInstance shader, blurShader, compositeShader, maskShader;
    private static VertexBuffer mesh;
    private static final Map<ResourceLocation, Optional<Material>> MATERIALS = new HashMap<>();
    private static final Map<RenderType, RenderType> TYPES = new IdentityHashMap<>();
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

    public static void updateCelestialLight(float partialTick) {
        if (shader == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimensionType().hasSkyLight()
                || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            shader.safeGetUniform("CelestialDirection").set(0f, 1f, 0f);
            shader.safeGetUniform("CelestialColor").set(0f, 0f, 0f);
            shader.safeGetUniform("SkyAmbient").set(0f);
            shader.safeGetUniform("CelestialVisibility").set(0f);
            shader.safeGetUniform("LocalSkyExposure").set(0f);
            return;
        }
        var light = CelestialLight.sample(level.getTimeOfDay(partialTick), level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick), level.getMoonBrightness());
        shader.safeGetUniform("CelestialDirection").set(light.x(), light.y(), 0f);
        shader.safeGetUniform("CelestialColor").set(light.red(), light.green(), light.blue());
        shader.safeGetUniform("SkyAmbient").set(light.ambient());
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var origin = camera.getPosition();
        // A bounded ray catches ceilings and walls in the light's actual direction.
        var end = origin.add(new Vec3(light.x(), light.y(), 0).scale(96));
        boolean visible = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, camera.getEntity())).getType() == HitResult.Type.MISS;
        shader.safeGetUniform("CelestialVisibility").set(visible ? 1f : 0f);
        shader.safeGetUniform("LocalSkyExposure").set(level.getBrightness(LightLayer.SKY, BlockPos.containing(origin)) / 15f);
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
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "gun_pbr"), DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_mask"), DefaultVertexFormat.POSITION_TEX), s -> maskShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_blur"), DefaultVertexFormat.POSITION_TEX), s -> blurShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("taczfixes", "pbr_composite"), DefaultVertexFormat.POSITION_TEX), s -> compositeShader = s);
    }

    public static RenderType resolve(RenderType original) {
        if (!enabled() || original instanceof PbrType || original.format() != DefaultVertexFormat.NEW_ENTITY
                || !(original instanceof PbrCompositeAccessor composite)) return original;
        return TYPES.computeIfAbsent(original, key -> {
            var textureState = ((PbrStateAccessor) (Object) composite.taczfixes$pbrState()).taczfixes$pbrTexture();
            var texture = ((PbrTextureAccessor) textureState).taczfixes$pbrTextureLocation();
            if (texture.isEmpty()) return original;
            var material = MATERIALS.computeIfAbsent(texture.get(), PbrRenderer::findMaterial);
            return material.<RenderType>map(m -> new PbrType(original, m)).orElse(original);
        });
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
                if (material.specular() != null) RenderSystem.setShaderTexture(3, material.specular());
                if (material.normal() != null) RenderSystem.setShaderTexture(4, material.normal());
                shader.safeGetUniform("HasSpecular").set(material.specular() == null ? 0 : 1);
                shader.safeGetUniform("HasNormal").set(material.normal() == null ? 0 : 1);
                shader.safeGetUniform("ReflectionStrength").set(material.specular() == null ? 0f : Config.PBR_REFLECTION.get().floatValue());
                shader.safeGetUniform("EmissionStrength").set(Config.PBR_EMISSION.get().floatValue());
                shader.safeGetUniform("EmissionPass").set(0);
                RenderSystem.setShader(() -> shader);
                mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);

                if (PbrBloom.canCapture() && (!isTracerBloomSuppressed() || Config.PBR_BLOOM_TRACER.get())
                        && (material.emissive() || PbrBloom.hasEmissiveContent())
                        && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                        == Minecraft.getInstance().getMainRenderTarget().frameBufferId) {
                    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                    try {
                        RenderSystem.depthMask(true);
                        PbrBloom.bindCapture();
                        shader.safeGetUniform("EmissionPass").set(1);
                        mesh.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
                    } finally {
                        shader.safeGetUniform("EmissionPass").set(0);
                        RenderSystem.depthMask(depthMask);
                        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
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