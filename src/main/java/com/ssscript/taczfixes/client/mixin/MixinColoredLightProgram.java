package com.ssscript.taczfixes.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.light.ShaderInjection;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * 把彩色光代码注入原版着色器源码(移植自 Shimmer, MIT)。
 */
@Mixin(Program.class)
public abstract class MixinColoredLightProgram {

    @ModifyExpressionValue(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
    private static String taczfixes$injectColoredLight(String shader, Program.Type type, String shaderName,
                                                       InputStream pShaderDataSame, String pShaderSourceName,
                                                       GlslPreprocessor processor) {
        boolean isVsh = type == Program.Type.VERTEX;
        String injectedShader;
        if (isVsh && ShaderInjection.hasInjectVSH(shaderName)) {
            injectedShader = ShaderInjection.injectVSH(shaderName, shader);
        } else if (type == Program.Type.FRAGMENT && ShaderInjection.hasInjectFSH(shaderName)) {
            injectedShader = ShaderInjection.injectFSH(shaderName, shader);
        } else {
            return shader;
        }

        int testShaderId = GlStateManager.glCreateShader(type == Program.Type.VERTEX ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
        GlStateManager.glShaderSource(testShaderId, processor.process(injectedShader));
        GlStateManager.glCompileShader(testShaderId);
        if (GlStateManager.glGetShaderi(testShaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String errorInfo = StringUtils.trim(GlStateManager.glGetShaderInfoLog(testShaderId, Short.MAX_VALUE));
            GlStateManager.glDeleteShader(testShaderId);
            TaczFixesMod.LOGGER.error("Couldn't compile injected {} program({},{}):{}", type.name(), pShaderSourceName, shaderName, errorInfo);
            return shader;
        }

        taczfixes$clearImportedPathRecord(processor);

        GlStateManager.glDeleteShader(testShaderId);
        return injectedShader;
    }

    /** 清空 GlslPreprocessor 已导入路径记录, 否则真实编译时 #moj_import 会重复导入被跳过(着色器缺函数)。
     *  字段名是混淆的, 按"唯一 Set 字段"定位。 */
    @Unique
    private static void taczfixes$clearImportedPathRecord(Object processor) {
        try {
            for (Field field : processor.getClass().getDeclaredFields()) {
                if (!Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(processor);
                if (value instanceof Set<?> set) {
                    set.clear();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
