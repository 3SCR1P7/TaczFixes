package com.ssscript.taczfixes.client.util;

import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 检测 Iris / Oculus 光影包是否正在使用(带 1 秒缓存)。
 *
 * 光影包启用时会与枪械 PBR 渲染管线冲突, 因此本模组在光影启用期间强制关闭枪械 PBR 功能。
 * 检测优先使用 Iris API v0(Iris 与 Oculus 都实现该接口), 没有 API 时回退读取 config 下的
 * iris.properties / oculus.properties(enableShaders=true 且指定了 shaderPack)。
 */
public final class IrisShaderCompat {
    private static final long CHECK_INTERVAL_NANOS = 1_000_000_000L;

    private static volatile boolean shaderPackInUse;
    // 注意: 不能用 Long.MIN_VALUE 作哨兵(nanoTime - Long.MIN_VALUE 会溢出为负数, 导致永远不检测)
    private static volatile long lastCheckNanos = -CHECK_INTERVAL_NANOS;
    /** 首次检测时输出一条日志, 方便确认检测逻辑确实在运行。 */
    private static volatile boolean notified;

    private IrisShaderCompat() {
    }

    /** 光影包是否正在使用。 */
    public static boolean isShaderPackInUse() {
        long now = System.nanoTime();
        if (now - lastCheckNanos >= CHECK_INTERVAL_NANOS) {
            lastCheckNanos = now;
            boolean current = detect();
            if (!notified) {
                notified = true;
                TaczFixesMod.LOGGER.info("Iris/Oculus detection started: shader pack in use = {}", current);
            }
            if (current != shaderPackInUse) {
                shaderPackInUse = current;
                if (current) {
                    TaczFixesMod.LOGGER.info("Iris/Oculus shader pack is in use; gun PBR disabled to avoid shader pipeline conflicts");
                } else {
                    TaczFixesMod.LOGGER.info("Iris/Oculus shader pack disabled; gun PBR re-enabled");
                }
            }
        }
        return shaderPackInUse;
    }

    private static boolean detect() {
        Boolean api = detectIrisApi();
        if (api != null) return api;
        return detectConfig();
    }

    /** Iris API v0; Iris 与 Oculus 都提供这个接口, 没有安装时返回 null 走配置回退。 */
    private static Boolean detectIrisApi() {
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = apiClass.getMethod("getInstance").invoke(null);
            if (instance == null) return false;
            Object result = apiClass.getMethod("isShaderPackInUse").invoke(instance);
            return result instanceof Boolean value ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 读取 config/iris.properties、config/oculus.properties 回退判断。 */
    private static boolean detectConfig() {
        try {
            Path configDir = configDir();
            if (configDir == null || !Files.isDirectory(configDir)) return false;
            for (String name : new String[]{"iris.properties", "oculus.properties"}) {
                Path file = configDir.resolve(name);
                if (!Files.isRegularFile(file)) continue;
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                }
                boolean enabled = Boolean.parseBoolean(
                        properties.getProperty("enableShaders", "false").trim());
                String pack = properties.getProperty("shaderPack", "").trim();
                if (enabled && !pack.isEmpty()) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Path configDir() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath().resolve("config");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
