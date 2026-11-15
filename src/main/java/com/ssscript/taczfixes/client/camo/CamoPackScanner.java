package com.ssscript.taczfixes.client.camo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 扫描 gameDir/tacz 下的枪包(目录或 zip), 读取动态迷彩声明与片段:
 *
 * <pre>
 * data/&lt;namespace&gt;/camo/&lt;name&gt;.json         声明
 * assets/&lt;namespace&gt;/camo/&lt;name&gt;.glsl       片段(也可用 "shader" 显式指定位置)
 * </pre>
 *
 * 声明示例: {@code {"name": "...", "shader": "&lt;ns&gt;:camo/&lt;name&gt;", "strength": 0.95, "params": [[...]]}}。

 * 该扫描不依赖 TaCZ 是否把枪包注册成资源包, 直接读磁盘。
 */
public final class CamoPackScanner {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final Gson GSON = new Gson();
    private static final String CAMO_DIR = "camo/";
    private static final String FUNCTION_NAME = "taczfixesCamo";
    /** 单个声明/片段文件读取上限(字符), 防止误把大文件当迷彩资源读进内存。 */
    private static final int MAX_FILE_CHARS = 1 << 18;

    private CamoPackScanner() {
    }

    /** 扫描全部枪包, 返回按 id 排序、去重后的迷彩定义。 */
    public static List<CamoDefinition> scan() {
        List<CamoDefinition> result = new ArrayList<>();
        Path taczDir = FMLPaths.GAMEDIR.get().resolve("tacz");
        if (!Files.isDirectory(taczDir)) {
            return result;
        }
        List<Path> packs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(taczDir)) {
            stream.filter(path -> Files.isDirectory(path)
                            || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted()
                    .forEach(packs::add);
        } catch (IOException ex) {
            LOGGER.warn("taczfixes: failed to list gun pack directory {}", taczDir, ex);
            return result;
        }
        Map<ResourceLocation, String> owners = new HashMap<>();
        for (Path pack : packs) {
            try {
                if (Files.isDirectory(pack)) {
                    scanDirectoryPack(pack, result, owners);
                } else {
                    scanZipPack(pack, result, owners);
                }
            } catch (Throwable t) {
                LOGGER.warn("taczfixes: failed to scan gun pack {} for dynamic camo", pack.getFileName(), t);
            }
        }
        result.sort(Comparator.comparing(definition -> definition.id().toString()));
        return result;
    }

    // ---------------- 目录枪包 ----------------

    private static void scanDirectoryPack(Path packDir, List<CamoDefinition> out, Map<ResourceLocation, String> owners) {
        Path dataDir = packDir.resolve("data");
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        DirPackReader reader = new DirPackReader(packDir);
        List<String> jsonFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dataDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> jsonFiles.add(packDir.relativize(path).toString().replace('\\', '/')));
        } catch (IOException ex) {
            LOGGER.warn("taczfixes: failed to walk gun pack data dir {}", dataDir, ex);
            return;
        }
        for (String relative : jsonFiles) {
            try {
                addDefinition(reader, relative, out, owners, packDir.getFileName().toString());
            } catch (Throwable t) {
                LOGGER.warn("taczfixes: failed to load camo definition {} from {}", relative, packDir.getFileName(), t);
            }
        }
    }

    // ---------------- zip 枪包 ----------------

    private static void scanZipPack(Path zipPath, List<CamoDefinition> out, Map<ResourceLocation, String> owners) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipPackReader reader = new ZipPackReader(zip);
            List<String> jsonFiles = new ArrayList<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".json")) {
                    jsonFiles.add(name);
                }
            }
            for (String relative : jsonFiles) {
                try {
                    addDefinition(reader, relative, out, owners, zipPath.getFileName().toString());
                } catch (Throwable t) {
                    LOGGER.warn("taczfixes: failed to load camo definition {} from {}", relative, zipPath.getFileName(), t);
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("taczfixes: failed to open gun pack zip {} for dynamic camo", zipPath.getFileName(), ex);
        }
    }

    // ---------------- 声明解析 ----------------

    private static void addDefinition(PackReader reader, String relativeJson, List<CamoDefinition> out,
                                      Map<ResourceLocation, String> owners, String packName) {
        ResourceLocation id = camoIdOf(relativeJson);
        if (id == null) {
            return;
        }
        String json = reader.read(relativeJson);
        if (json == null || json.isBlank()) {
            return;
        }
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element == null || !element.isJsonObject()) {
                LOGGER.warn("taczfixes: camo definition {} is not a JSON object", relativeJson);
                return;
            }
            root = element.getAsJsonObject();
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: failed to parse camo definition {} in {}: {}", relativeJson, packName, ex.getMessage());
            return;
        }
        String shaderSource = readShaderSource(reader, id, readString(root, "shader"));
        if (shaderSource == null) {
            LOGGER.warn("taczfixes: camo {} has no fragment at assets/{}/{}{}.glsl (or the path given by \"shader\")",
                    id, id.getNamespace(), CAMO_DIR, id.getPath());
            return;
        }
        if (!shaderSource.contains(FUNCTION_NAME)) {
            LOGGER.warn("taczfixes: camo {} fragment does not define {}(); skipped", id, FUNCTION_NAME);
            return;
        }
        String previous = owners.putIfAbsent(id, packName);
        if (previous != null) {
            LOGGER.warn("taczfixes: camo {} from {} ignored, already provided by {}", id, packName, previous);
            return;
        }
        String name = readString(root, "name");
        out.add(new CamoDefinition(id,
                name == null ? id.toString() : name,
                shaderSource,
                clamp01(readFloat(root, "strength", 1.0f)),
                clamp01(readFloat(root, "emissive", 0.0f)),
                readParams(root)));
    }

    /** data/&lt;ns&gt;/camo/&lt;name&gt;.json -&gt; &lt;ns&gt;:&lt;name&gt;。 */
    private static ResourceLocation camoIdOf(String relativeJson) {
        String path = relativeJson.replace('\\', '/');
        if (!path.startsWith("data/") || !path.endsWith(".json")) {
            return null;
        }
        int secondSlash = path.indexOf('/', "data/".length());
        if (secondSlash < 0) {
            return null;
        }
        String namespace = path.substring("data/".length(), secondSlash);
        String rest = path.substring(secondSlash + 1);
        String name;
        if (rest.startsWith(CAMO_DIR)) {
            name = rest.substring(CAMO_DIR.length());
        } else {
            return null;
        }
        name = name.substring(0, name.length() - ".json".length());
        if (name.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(namespace.toLowerCase(Locale.ROOT) + ":" + name);
    }

    /**
     * 片段位置, 依次尝试:
     * <ol>
     *   <li>声明里的 "shader": 相对 assets/&lt;ns&gt;/camo/ 的路径, 或 &lt;ns&gt;:&lt;相对 assets 的路径&gt;</li>
     *   <li>缺省: assets/&lt;ns&gt;/camo/&lt;name&gt;.glsl</li>
     * </ol>
     */
    private static String readShaderSource(PackReader reader, ResourceLocation id, String declared) {
        List<String> candidates = new ArrayList<>();
        if (declared != null && !declared.isBlank()) {
            if (declared.indexOf(':') > 0) {
                ResourceLocation shaderId = ResourceLocation.tryParse(declared);
                if (shaderId == null) {
                    return null;
                }
                addCandidates(candidates, "assets/" + shaderId.getNamespace() + "/" + shaderId.getPath());
            } else {
                addCandidates(candidates, "assets/" + id.getNamespace() + "/" + CAMO_DIR + declared);
            }
        } else {
            addCandidates(candidates, "assets/" + id.getNamespace() + "/" + CAMO_DIR + id.getPath());
        }
        for (String candidate : candidates) {
            String text = reader.read(candidate);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static void addCandidates(List<String> candidates, String base) {
        String path = base.replace('\\', '/');
        if (path.endsWith(".glsl") || path.endsWith(".fsh") || path.endsWith(".txt")) {
            candidates.add(path);
            return;
        }
        candidates.add(path + ".glsl");
        candidates.add(path);
        candidates.add(path + ".fsh");
        candidates.add(path + ".txt");
    }

    private static String readString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static float readFloat(JsonObject root, String key, float fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float[][] readParams(JsonObject root) {
        JsonElement element = root.get("params");
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray rows = element.getAsJsonArray();
        float[][] params = new float[Math.min(rows.size(), CamoDefinition.MAX_PARAMS)][];
        for (int row = 0; row < params.length; row++) {
            JsonElement rowElement = rows.get(row);
            if (rowElement == null || !rowElement.isJsonArray()) {
                params[row] = new float[0];
                continue;
            }
            JsonArray values = rowElement.getAsJsonArray();
            float[] line = new float[Math.min(values.size(), 4)];
            for (int col = 0; col < line.length; col++) {
                try {
                    line[col] = values.get(col).getAsFloat();
                } catch (Exception ignored) {
                    line[col] = 0.0f;
                }
            }
            params[row] = line;
        }
        return params;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        return value > 1.0f ? 1.0f : value;
    }

    // ---------------- 读取器 ----------------

    private interface PackReader {
        /** relativePath 为相对枪包根目录的路径(例如 data/xxx/... 或 assets/xxx/...), 读不到返回 null。 */
        String read(String relativePath);
    }

    private static final class DirPackReader implements PackReader {
        private final Path root;

        DirPackReader(Path root) {
            this.root = root;
        }

        @Override
        public String read(String relativePath) {
            Path file = root.resolve(relativePath);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try {
                if (Files.size(file) > MAX_FILE_CHARS) {
                    return null;
                }
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return null;
            }
        }
    }

    private static final class ZipPackReader implements PackReader {
        private final ZipFile zip;

        ZipPackReader(ZipFile zip) {
            this.zip = zip;
        }

        @Override
        public String read(String relativePath) {
            ZipEntry entry = zip.getEntry(relativePath);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                    builder.append(buffer, 0, read);
                    if (builder.length() > MAX_FILE_CHARS) {
                        return null;
                    }
                }
                return builder.toString();
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
