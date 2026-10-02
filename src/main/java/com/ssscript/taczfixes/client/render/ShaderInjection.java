package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.TaczFixesMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 核心着色器源码注入表。移植自 Shimmer (MIT)。
 */
public final class ShaderInjection {
    private static final Map<String, List<Function<String, String>>> VSH_INJECTIONS = new HashMap<>();
    private static final Map<String, List<Function<String, String>>> FSH_INJECTIONS = new HashMap<>();

    private ShaderInjection() {
    }

    public static void registerVSHInjection(String shaderName, Function<String, String> injection) {
        VSH_INJECTIONS.computeIfAbsent(shaderName, s -> new ArrayList<>()).add(injection);
    }

    public static boolean hasInjectFSH(String shaderName) {
        return FSH_INJECTIONS.containsKey(shaderName);
    }

    public static String injectFSH(String shaderName, String content) {
        TaczFixesMod.LOGGER.info("Injecting colored light into shader fsh {}.", shaderName);
        for (Function<String, String> function : FSH_INJECTIONS.getOrDefault(shaderName, Collections.emptyList())) {
            content = function.apply(content);
        }
        return content;
    }

    public static boolean hasInjectVSH(String shaderName) {
        return VSH_INJECTIONS.containsKey(shaderName);
    }

    public static String injectVSH(String shaderName, String content) {
        TaczFixesMod.LOGGER.info("Injecting colored light into shader vsh {}.", shaderName);
        for (Function<String, String> function : VSH_INJECTIONS.getOrDefault(shaderName, Collections.emptyList())) {
            content = function.apply(content);
        }
        return content;
    }
}
