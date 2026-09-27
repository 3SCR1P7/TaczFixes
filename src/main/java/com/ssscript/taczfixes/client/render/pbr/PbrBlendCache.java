package com.ssscript.taczfixes.client.render.pbr;

import com.mojang.blaze3d.shaders.BlendMode;
import com.ssscript.taczfixes.client.mixin.PbrBlendModeAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Keeps Minecraft's shader blend cache in sync with restored GL state. */
public final class PbrBlendCache {
    private static BlendMode saved;
    private static int equation;

    public static void save() {
        saved = PbrBlendModeAccessor.taczfixes$getLastApplied();
        equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    }

    public static void restore() {
        RenderSystem.blendEquation(equation);
        PbrBlendModeAccessor.taczfixes$setLastApplied(saved);
        saved = null;
    }

    private PbrBlendCache() {}
}
