package com.ssscript.taczfixes.client.compat;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;

public final class SodiumShaderState {
    private static final ThreadLocal<ShaderType> CURRENT = new ThreadLocal<>();

    private SodiumShaderState() {
    }

    public static void push(ShaderType type) {
        CURRENT.set(type);
    }

    public static void pop() {
        CURRENT.remove();
    }

    public static boolean vertex() {
        return CURRENT.get() == ShaderType.VERTEX;
    }
}
