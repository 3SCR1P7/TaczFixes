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
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid = TaczFixesMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AimBlur {
    private static ShaderInstance shader;
    private static ShaderInstance focusShader;
    private static AbstractUniform uNearPlane;
    private static AbstractUniform uFarPlane;
    private static AbstractUniform uMaxRadius;
    private static AbstractUniform uProgress;
    private static AbstractUniform uFocusNear;
    private static AbstractUniform uFocusFar;
    private static AbstractUniform uFocusBlend;
    private static AbstractUniform uFocusInitialized;
    private static RenderTarget dof;
    private static RenderTarget focusFront;
    private static RenderTarget focusBack;
    private static VertexBuffer quad;
    private static boolean focusHistoryValid;
    private static long lastFocusNanos;
    private static final Matrix4f IDENTITY = new Matrix4f();

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        release();
        shader = null;
        focusShader = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "aim_blur"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    shader = s;
                    uNearPlane = s.safeGetUniform("NearPlane");
                    uFarPlane = s.safeGetUniform("FarPlane");
                    uMaxRadius = s.safeGetUniform("MaxRadius");
                    uProgress = s.safeGetUniform("Progress");
                });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("taczfixes", "aim_focus"), DefaultVertexFormat.POSITION_TEX),
                s -> {
                    focusShader = s;
                    uFocusNear = s.safeGetUniform("NearPlane");
                    uFocusFar = s.safeGetUniform("FarPlane");
                    uFocusBlend = s.safeGetUniform("Blend");
                    uFocusInitialized = s.safeGetUniform("Initialized");
                });
    }

    public static void composite() {
        if (shader == null) {
            resetFocus();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            resetFocus();
            return;
        }
        AimingParams params = resolveParams(player);
        if (!params.enabled()) {
            resetFocus();
            return;
        }
        float progress = operator.getClientAimingProgress(mc.getFrameTime());
        if (progress <= 0.01f) {
            resetFocus();
            return;
        }
        float maxRadius = params.strength() * 2.5f * params.range() * progress;
        if (maxRadius < 0.35f) {
            resetFocus();
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) {
            resetFocus();
            return;
        }
        ensureTargets(main);
        if (dof == null || focusShader == null || focusFront == null || focusBack == null) {
            resetFocus();
            return;
        }

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        float m22 = projection.m22();
        float m23 = projection.m23();
        float near = m23 / (m22 - 1.0f);
        float far = m23 / (m22 + 1.0f);
        if (!Float.isFinite(near) || !Float.isFinite(far) || near <= 0.0f || far <= near) {
            near = 0.05f;
            far = 512.0f;
        }

        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        PbrBlendCache.save();
        int[] textures = new int[3];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = RenderSystem.getShaderTexture(i);
        }
        ShaderInstance previous = RenderSystem.getShader();
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            ensureQuad();

            uNearPlane.set(near);
            uFarPlane.set(far);
            uMaxRadius.set(maxRadius);
            uProgress.set(progress);
            updateFocus(main.getDepthTextureId(), near, far, params.focusTransition());
            draw(dof, main.getColorTextureId(), main.getDepthTextureId(), focusFront.getColorTextureId());

            uMaxRadius.set(0.0f);
            uProgress.set(0.0f);
            draw(main, dof.getColorTextureId(), 0, focusFront.getColorTextureId());
        } finally {
            VertexBuffer.unbind();
            main.bindWrite(true);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.setShaderTexture(i, textures[i]);
            }
            RenderSystem.setShader(() -> previous);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            PbrBlendCache.restore();
            RenderSystem.depthMask(depthMask);
            if (stencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            }
        }
    }

    private record AimingParams(boolean enabled, float range, float strength, float focusTransition) {
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
        float focusTransition = Config.AIM_BLUR_FOCUS_TRANSITION.get().floatValue();
        return new AimingParams(enabled, range, strength, focusTransition);
    }

    private static AimingBlurConfig resolveDisplayConfig(LocalPlayer player) {
        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return null;
        }
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
        if (slotId == null || DefaultAssets.isEmptyAttachmentId(slotId)) {
            return null;
        }
        Optional<CommonAttachmentIndex> indexOpt = TimelessAPI.getCommonAttachmentIndex(slotId);
        if (indexOpt.isEmpty()) {
            return null;
        }
        ResourceLocation displayId = indexOpt.get().getPojo().getDisplay();
        if (displayId == null) {
            return null;
        }
        AttachmentDisplay display = ClientAssetsManager.INSTANCE.getAttachmentDisplay(displayId);
        if (!(display instanceof AimingBlurDisplayAccessor accessor)) {
            return null;
        }
        return accessor.getAimingBlur();
    }

    private static void ensureTargets(RenderTarget main) {
        if (dof != null && dof.width == main.width && dof.height == main.height) {
            ensureFocusTargets();
            return;
        }
        releaseTargets();
        dof = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        dof.setFilterMode(GL11.GL_LINEAR);
        ensureFocusTargets();
    }

    private static void ensureFocusTargets() {
        if (focusFront != null && focusBack != null) {
            return;
        }
        if (focusFront != null) {
            focusFront.destroyBuffers();
            focusFront = null;
        }
        if (focusBack != null) {
            focusBack.destroyBuffers();
            focusBack = null;
        }
        focusFront = new TextureTarget(1, 1, false, Minecraft.ON_OSX);
        focusFront.setFilterMode(GL11.GL_NEAREST);
        focusBack = new TextureTarget(1, 1, false, Minecraft.ON_OSX);
        focusBack.setFilterMode(GL11.GL_NEAREST);
        resetFocus();
    }

    private static void updateFocus(int depthTexture, float near, float far, float transitionSeconds) {
        long now = System.nanoTime();
        float blend;
        if (!focusHistoryValid || transitionSeconds <= 0.001f) {
            blend = 1.0f;
        } else {
            float dt = (now - lastFocusNanos) / 1.0e9f;
            if (dt > 0.25f) {
                blend = 1.0f;
            } else {
                blend = 1.0f - (float) Math.exp(-Math.max(dt, 0.0f) / transitionSeconds);
            }
        }
        lastFocusNanos = now;
        uFocusNear.set(near);
        uFocusFar.set(far);
        uFocusBlend.set(blend);
        uFocusInitialized.set(focusHistoryValid ? 1.0f : 0.0f);

        focusBack.bindWrite(true);
        RenderSystem.setShaderTexture(0, focusFront.getColorTextureId());
        RenderSystem.setShaderTexture(1, depthTexture);
        RenderSystem.setShader(() -> focusShader);
        quad.bind();
        quad.drawWithShader(IDENTITY, IDENTITY, focusShader);

        RenderTarget temp = focusFront;
        focusFront = focusBack;
        focusBack = temp;
        focusHistoryValid = true;
    }

    private static void resetFocus() {
        focusHistoryValid = false;
        lastFocusNanos = 0L;
    }

    private static void draw(RenderTarget output, int colorTexture, int depthTexture, int focusTexture) {
        output.bindWrite(true);
        RenderSystem.setShaderTexture(0, colorTexture);
        RenderSystem.setShaderTexture(1, depthTexture);
        RenderSystem.setShaderTexture(2, focusTexture);
        RenderSystem.setShader(() -> shader);
        quad.bind();
        quad.drawWithShader(IDENTITY, IDENTITY, shader);
    }

    private static void ensureQuad() {
        if (quad != null) {
            return;
        }
        BufferBuilder builder = new BufferBuilder(128);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1, -1, 0).uv(0, 0).endVertex();
        builder.vertex(1, -1, 0).uv(1, 0).endVertex();
        builder.vertex(1, 1, 0).uv(1, 1).endVertex();
        builder.vertex(-1, 1, 0).uv(0, 1).endVertex();
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(builder.end());
    }

    private static void releaseTargets() {
        if (dof != null) {
            dof.destroyBuffers();
            dof = null;
        }
        if (focusFront != null) {
            focusFront.destroyBuffers();
            focusFront = null;
        }
        if (focusBack != null) {
            focusBack.destroyBuffers();
            focusBack = null;
        }
        resetFocus();
    }

    public static void release() {
        releaseTargets();
        if (quad != null) {
            quad.close();
            quad = null;
        }
    }

    private AimBlur() {
    }
}
