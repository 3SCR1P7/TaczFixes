package com.ssscript.taczfixes.client.mixin.shader;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ssscript.taczfixes.TaczFixesMod;
import com.ssscript.taczfixes.client.render.ShaderInjection;
import com.ssscript.taczfixes.client.render.ShaderSourceValidator;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * 把彩色光代码注入原版着色器源码(移植自 Shimmer, MIT)。
 * 注入是"尽力而为": 先试编译, 编译不过或源码结构不认识就原样放回, 绝不让游戏崩溃。
 */
@Mixin(Program.class)
public abstract class MixinColoredLightProgram {

    @ModifyExpressionValue(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
    private static String taczfixes$injectColoredLight(String shader, Program.Type type, String shaderName,
                                                       InputStream pShaderDataSame, String pShaderSourceName,
                                                       GlslPreprocessor processor) {
        if (type != Program.Type.VERTEX || !ShaderInjection.hasInjectVSH(shaderName)) {
            return shader;
        }

        String injectedShader;
        try {
            injectedShader = ShaderInjection.injectVSH(shaderName, shader);
        } catch (Throwable t) {
            TaczFixesMod.LOGGER.debug("Could not inject colored light into shader {}", shaderName, t);
            return shader;
        }
        if (injectedShader == null || injectedShader.equals(shader)) {
            return shader;
        }

        // GlslPreprocessor 的"已导入路径"记录是共享状态: 试编译前后都要清空,
        // 否则紧接着的真实编译会跳过 #moj_import(着色器缺函数)。
        taczfixes$clearImportedPathRecord(processor);
        boolean compiles;
        try {
            List<String> processed = processor.process(injectedShader);
            compiles = ShaderSourceValidator.compilesVertex(processed);
        } catch (Throwable t) {
            compiles = false;
        } finally {
            taczfixes$clearImportedPathRecord(processor);
        }
        if (!compiles) {
            TaczFixesMod.LOGGER.debug("Discarding colored light injection for shader {} because the driver rejected it", shaderName);
            return shader;
        }
        TaczFixesMod.LOGGER.info("Injecting colored light into shader vsh {}.", shaderName);
        return injectedShader;
    }

    /** 清空 GlslPreprocessor 已导入路径记录。字段名是混淆的, 遍历类继承链清空所有 Set 字段。 */
    @Unique
    private static void taczfixes$clearImportedPathRecord(Object processor) {
        if (processor == null) {
            return;
        }
        try {
            for (Class<?> type = processor.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Set.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(processor);
                    if (value instanceof Set<?> set) {
                        set.clear();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
