package com.ssscript.taczfixes.client.mixin.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.ssscript.taczfixes.client.render.PbrRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录 OpenGL 着色掩码状态(阴影阶段会关掉颜色写入)。
 * PBR 几何路由原本对每个模型部件都做一次 glGetBooleanv(GL_COLOR_WRITEMASK),
 * 这里把它变成一次普通字段读取。
 */
@Mixin(GlStateManager.class)
public class MixinGlStateManagerColorMask {

    @Inject(method = "_colorMask(ZZZZ)V", at = @At("HEAD"))
    private static void taczfixes$trackColorMask(boolean red, boolean green, boolean blue, boolean alpha,
                                                 CallbackInfo ci) {
        PbrRenderer.setTrackedColorMask(red, green, blue);
    }
}
