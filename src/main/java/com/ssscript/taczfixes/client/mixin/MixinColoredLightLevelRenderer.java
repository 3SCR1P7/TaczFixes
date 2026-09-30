package com.ssscript.taczfixes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ssscript.taczfixes.client.render.light.LightManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界渲染前后上传/清理彩色光 UBO(移植自 Shimmer, MIT)。
 */
@Mixin(LevelRenderer.class)
public abstract class MixinColoredLightLevelRenderer {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void taczfixes$coloredLightPre(PoseStack poseStack, float partialTick, long finishNanoTime,
                                           boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                           LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        Vec3 position = camera.getPosition();
        LightManager.INSTANCE.renderLevelPre(0, (float) position.x, (float) position.y, (float) position.z);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczfixes$coloredLightPost(PoseStack poseStack, float partialTick, long finishNanoTime,
                                            boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                            LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        LightManager.INSTANCE.renderLevelPost();
    }
}
