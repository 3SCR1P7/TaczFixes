package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ssscript.taczfixes.TaczFixesMod;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT)
public final class MuzzleSmokeRender extends RenderStateShard {
    private static ShaderInstance handShader;
    private static ShaderInstance worldShader;
    private static final Function<ResourceLocation, RenderType> HAND_TYPES =
            Util.memoize(MuzzleSmokeRender::createHandType);
    private static final Function<ResourceLocation, ParticleRenderType> WORLD_TYPES =
            Util.memoize(WorldType::new);

    private MuzzleSmokeRender() {
        super("taczfixes_muzzle_smoke", () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "muzzle_smoke_hand"),
                DefaultVertexFormat.NEW_ENTITY), shader -> handShader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(TaczFixesMod.MOD_ID, "muzzle_smoke_world"),
                DefaultVertexFormat.PARTICLE), shader -> worldShader = shader);
    }

    public static boolean hasHandShader() {
        return handShader != null;
    }

    public static RenderType hand(ResourceLocation texture) {
        return HAND_TYPES.apply(texture);
    }

    public static ParticleRenderType world(ResourceLocation texture, ParticleRenderType fallback) {
        return worldShader == null ? fallback : WORLD_TYPES.apply(texture);
    }

    private static RenderType createHandType(ResourceLocation texture) {
        return RenderType.create("taczfixes_muzzle_smoke_hand", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true, RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> handShader))
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static final class WorldType implements ParticleRenderType {
        private final TextureStateShard textureState;

        private WorldType(ResourceLocation texture) {
            textureState = new TextureStateShard(texture, true, false);
        }

        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            textureState.setupRenderState();
            RenderSystem.setShader(() -> worldShader);
            RenderSystem.depthMask(false);
            TRANSLUCENT_TRANSPARENCY.setupRenderState();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "taczfixes_muzzle_smoke_world";
        }
    }
}
