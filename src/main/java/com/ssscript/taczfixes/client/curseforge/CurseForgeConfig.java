package com.ssscript.taczfixes.client.curseforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CurseForge 下载功能设置, 持久化于 config/taczfixes_curseforge.json。
 *
 * 默认走国内可直连的 CurseForge 镜像(无需 API Key); 想用官方 API 时填写个人 API Key,
 * 并把 API 地址改成 https://api.curseforge.com/v1 (文件地址留空即可自动推导)。
 * Key 仅保存在本地, 不打包进 mod, 也不会上传到任何地方。
 */
public final class CurseForgeConfig {
    /** 国内镜像, 无需 Key。 */
    public static final String DEFAULT_API_BASE = "https://mod.mcimirror.top/curseforge/v1";
    public static final String DEFAULT_FILE_BASE = "https://mod.mcimirror.top/files";
    public static final String OFFICIAL_API_BASE = "https://api.curseforge.com/v1";
    private static final String FILE_NAME = "taczfixes_curseforge.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String apiKey = "";
    private static String apiBase = DEFAULT_API_BASE;
    private static String fileBase = DEFAULT_FILE_BASE;
    private static String sortMode = "DOWNLOADS";
    private static boolean loaded = false;

    private CurseForgeConfig() {
    }

    /**
     * 优先使用 NeoForge 的 config 目录(FMLPaths.CONFIGDIR); 用反射访问, 避免在极早期类加载时
     * 触发加载器初始化, 也方便脱离游戏环境做单元冒烟测试。
     */
    private static Path file() {
        try {
            Class<?> fmlPaths = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object configDir = fmlPaths.getField("CONFIGDIR").get(null);
            Object value = configDir.getClass().getMethod("get").invoke(configDir);
            if (value instanceof Path path) {
                return path.resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("config", FILE_NAME);
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) return;
            JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();
            apiKey = sanitizeKey(string(root, "apiKey", apiKey));
            apiBase = sanitizeBase(string(root, "apiBase", apiBase), DEFAULT_API_BASE);
            fileBase = sanitizeBase(string(root, "fileBase", fileBase), DEFAULT_FILE_BASE);
            sortMode = string(root, "sortMode", sortMode);
        } catch (Throwable ignored) {
        }
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return fallback;
        String value = root.get(key).getAsString();
        return value == null ? fallback : value.trim();
    }

    /** 只保留可打印 ASCII 并去掉换行, 避免非法的 HTTP 头值。 */
    public static String sanitizeKey(String raw) {
        if (raw == null) return "";
        String trimmed = raw.replaceAll("[\\r\\n\\t]", "").trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= 32 && c < 127) builder.append(c);
        }
        return builder.toString();
    }

    /** 空串返回 fallback; 非法 URL 也返回 fallback, 保证后续 URI 构造不会抛异常。 */
    public static String sanitizeBase(String raw, String fallback) {
        if (raw == null) return fallback;
        String value = raw.replaceAll("[\\r\\n\\t]", "").trim();
        if (value.isEmpty()) return fallback;
        if (!isValidBase(value)) return fallback;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? fallback : value;
    }

    /** 是否为合法的 http(s) 绝对地址(可带路径, 不带查询串)。 */
    public static boolean isValidBase(String value) {
        if (value == null) return false;
        String trimmed = value.replaceAll("[\\r\\n\\t]", "").trim();
        if (trimmed.isEmpty()) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c <= 32 || c >= 127) return false;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static synchronized String apiKey() {
        ensureLoaded();
        return apiKey;
    }

    public static synchronized String apiBase() {
        ensureLoaded();
        return apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
    }

    public static synchronized String sortMode() {
        ensureLoaded();
        return sortMode;
    }

    public static synchronized void setSortMode(String mode) {
        ensureLoaded();
        sortMode = mode == null ? "DOWNLOADS" : mode;
        save();
    }

    public static synchronized String fileBase() {
        ensureLoaded();
        if (fileBase == null || fileBase.isBlank()) return defaultFileBaseFor(apiBase());
        return fileBase;
    }

    /** 官方 API 使用 edge.forgecdn.net, 镜像 API 使用镜像的文件代理。 */
    public static String defaultFileBaseFor(String api) {
        String value = api == null ? "" : api;
        if (value.contains("mcimirror.top") || value.isBlank()) return DEFAULT_FILE_BASE;
        if (value.startsWith("https://api.curseforge.com")) return "https://edge.forgecdn.net/files";
        return DEFAULT_FILE_BASE;
    }

    /** @return true 表示保存成功 */
    public static synchronized boolean update(String key, String api, String files) {
        ensureLoaded();
        apiKey = sanitizeKey(key);
        apiBase = api == null || api.trim().isEmpty() ? DEFAULT_API_BASE : sanitizeBase(api, DEFAULT_API_BASE);
        fileBase = files == null || files.trim().isEmpty() ? "" : sanitizeBase(files, DEFAULT_FILE_BASE);
        return save();
    }

    /** @return true 表示写入成功 */
    public static synchronized boolean save() {
        JsonObject root = new JsonObject();
        root.addProperty("apiKey", apiKey);
        root.addProperty("apiBase", apiBase);
        root.addProperty("fileBase", fileBase);
        root.addProperty("sortMode", sortMode);
        try {
            Path path = file();
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
