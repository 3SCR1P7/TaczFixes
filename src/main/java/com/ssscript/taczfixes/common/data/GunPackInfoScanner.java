package com.ssscript.taczfixes.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 扫描 TaCZ 的枪包并读取 gunpack_info.json(名称/描述/logo)。
 * 无 logo 的枪包回退到 TaCZ 默认枪包(tacz_default_gun)的 logo;
 * 名称/描述若为本地化键, 直接读取枪包自身的 lang 文件解析, 避免新拖入的枪包在资源重载前显示键名。
 */
public final class GunPackInfoScanner {
    private static final String DEFAULT_PACK_DIR = "tacz_default_gun";
    private static final String DEFAULT_PACK_NAMESPACE = "tacz";
    private static final ResourceLocation FALLBACK_LOGO = new ResourceLocation("tacz", "textures/pack.png");

    public record PackInfoData(String id, String name, Path path, Path iconPath, Component displayName,
                               Component description, ResourceLocation logo) {
    }

    private record LogoSource(Path path, ResourceLocation logo) {
    }

    private GunPackInfoScanner() {
    }

    public static List<PackInfoData> scan(Path taczDir) {
        return scan(taczDir, "en_us");
    }

    public static List<PackInfoData> scan(Path taczDir, String language) {
        List<PackInfoData> result = new ArrayList<>();
        LogoSource defaultLogo = readDefaultLogo(taczDir);
        try {
            java.lang.reflect.Method scan = Class.forName("com.tacz.guns.resource.GunPackLoader")
                    .getDeclaredMethod("scanExtensions", Path.class);
            scan.setAccessible(true);
            GunPackHooks.RAW_SCAN.set(Boolean.TRUE);
            List<?> scanned;
            try {
                scanned = (List<?>) scan.invoke(null, taczDir);
            } finally {
                GunPackHooks.RAW_SCAN.set(Boolean.FALSE);
            }
            if (scanned == null) return result;
            for (Object pack : scanned) {
                String name = GunPackHooks.nameOf(pack);
                Path path = GunPackHooks.pathOf(pack);
                if (name == null || path == null) continue;
                result.add(read(path, name, defaultLogo, language));
            }
        } catch (Throwable ignored) {
        }
        return decorateDuplicateNames(result);
    }

    /** 同名命名空间有多个枪包时, 在显示名后追加文件名以便区分。 */
    private static List<PackInfoData> decorateDuplicateNames(List<PackInfoData> packs) {
        Map<String, Integer> counts = new HashMap<>();
        for (PackInfoData info : packs) {
            counts.merge(info.name(), 1, Integer::sum);
        }
        List<PackInfoData> result = new ArrayList<>(packs.size());
        for (PackInfoData info : packs) {
            if (counts.getOrDefault(info.name(), 0) <= 1) {
                result.add(info);
                continue;
            }
            String fileName = info.path().getFileName() == null ? info.name() : info.path().getFileName().toString();
            Component displayName = Component.literal(info.displayName().getString() + " " + fileName);
            result.add(new PackInfoData(info.id(), info.name(), info.path(), info.iconPath(),
                    displayName, info.description(), info.logo()));
        }
        return result;
    }

    /** 默认枪包的 logo(不存在时退化为固定路径)。 */
    private static LogoSource readDefaultLogo(Path taczDir) {
        Path defaultPath = taczDir.resolve(DEFAULT_PACK_DIR);
        ResourceLocation logo = readLogo(defaultPath, DEFAULT_PACK_NAMESPACE);
        return new LogoSource(defaultPath, logo == null ? FALLBACK_LOGO : logo);
    }

    private static PackInfoData read(Path path, String namespace, LogoSource defaultLogo, String language) {
        String raw = readInfo(path, namespace);
        Component displayName = Component.literal(namespace);
        Component description = Component.empty();
        ResourceLocation logo = null;
        String nameKey = null;
        String descKey = null;
        if (raw != null) {
            try {
                JsonElement element = JsonParser.parseString(raw);
                if (element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();
                    nameKey = string(root, "name");
                    descKey = string(root, "desc");
                    logo = parseLogo(root, namespace);
                }
            } catch (Throwable ignored) {
            }
        }
        Map<String, String> lang = readLang(path, namespace, language);
        displayName = text(nameKey, namespace, lang);
        description = text(descKey, "", lang);
        if (description.getString().isEmpty()) {
            String fileName = path.getFileName() == null ? namespace : path.getFileName().toString();
            description = Component.literal(fileName);
        }
        // 有自带 logo 就用自身枪包, 否则使用默认枪包的 logo。
        Path iconPath = path;
        if (logo == null) {
            logo = defaultLogo.logo();
            iconPath = defaultLogo.path();
        }
        return new PackInfoData(GunPackIds.of(namespace, path), namespace, path, iconPath, displayName, description, logo);
    }

    private static String string(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return null;
        String value = root.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    /** 名称/描述: 若为本地化键则先查枪包自身 lang, 查不到才用 translatable(资源加载后自动生效)。 */
    private static Component text(String value, String fallback, Map<String, String> lang) {
        if (value == null) {
            return fallback.isEmpty() ? Component.empty() : Component.literal(fallback);
        }
        if (!value.contains(".")) {
            return Component.literal(value);
        }
        String resolved = lang.get(value);
        return resolved != null ? Component.literal(resolved) : Component.translatable(value);
    }

    /** 读取枪包自身的 lang 文件(优先当前语言, 其次 en_us/zh_cn), 用于立即解析本地化键。 */
    private static Map<String, String> readLang(Path path, String namespace, String language) {
        Map<String, String> result = new HashMap<>();
        for (String code : new String[]{language, "en_us", "zh_cn"}) {
            if (code == null || result.containsKey(code)) continue;
            String raw = readPackFile(path, "assets/" + namespace + "/lang/" + code + ".json");
            if (raw == null) continue;
            try {
                JsonElement element = JsonParser.parseString(raw);
                if (!element.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        result.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                if (!result.isEmpty()) break;
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static ResourceLocation readLogo(Path path, String namespace) {
        String raw = readInfo(path, namespace);
        if (raw == null) return null;
        try {
            JsonElement element = JsonParser.parseString(raw);
            if (element.isJsonObject()) {
                return parseLogo(element.getAsJsonObject(), namespace);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static ResourceLocation parseLogo(JsonObject root, String namespace) {
        String logoField = string(root, "logo");
        if (logoField == null) return null;
        return logoField.contains(":")
                ? ResourceLocation.tryParse(logoField)
                : ResourceLocation.tryParse(namespace + ":" + logoField);
    }

    private static String readInfo(Path path, String namespace) {
        return readPackFile(path, "assets/" + namespace + "/gunpack_info.json");
    }

    /** 读取枪包内文件(目录或 zip, zip 允许外层包裹一层文件夹)。 */
    private static String readPackFile(Path packPath, String relative) {
        try {
            if (Files.isDirectory(packPath)) {
                Path file = packPath.resolve(relative);
                if (Files.exists(file)) return Files.readString(file, StandardCharsets.UTF_8);
                return null;
            }
            if (!Files.isRegularFile(packPath)) return null;
            try (ZipFile zip = new ZipFile(packPath.toFile(), StandardCharsets.UTF_8)) {
                ZipEntry entry = findEntry(zip, relative);
                if (entry == null) return null;
                try (InputStream stream = zip.getInputStream(entry)) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static ZipEntry findEntry(ZipFile zip, String relative) {
        String suffix = relative.startsWith("/") ? relative : "/" + relative;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.equals(relative) || name.endsWith(suffix)) {
                return entry;
            }
        }
        return null;
    }
}
