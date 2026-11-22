package com.ssscript.taczfixes.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.List;

/**
 * 彩色光注入前的试编译校验: 注入后的源码编译不通过就整段放弃注入。
 * 这样即使别的 mod 改过着色器结构, 也不会因为我们插错位置而让整个渲染阶段崩溃。
 */
public final class ShaderSourceValidator {
    private ShaderSourceValidator() {
    }

    public static boolean compilesVertex(List<String> lines) {
        return compiles(GL20.GL_VERTEX_SHADER, lines);
    }

    public static boolean compiles(int glShaderType, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        int shaderId = GlStateManager.glCreateShader(glShaderType);
        try {
            GlStateManager.glShaderSource(shaderId, lines);
            GlStateManager.glCompileShader(shaderId);
            return GlStateManager.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) != GL11.GL_FALSE;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GlStateManager.glDeleteShader(shaderId);
        }
    }
}
