package com.ssscript.taczfixes.client.util.camo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 把枪包提供的着色器片段拼进 taczfixes 的 gun_pbr 片段着色器, 生成一个专属程序:
 *
 * <pre>
 * gun_pbr.fsh 里 //&lt;&lt;&lt;TACZFIXES_CAMO_BEGIN&gt;&gt;&gt; .. //&lt;&lt;&lt;TACZFIXES_CAMO_END&gt;&gt;&gt; 之间整段被替换成:
 *   每条迷彩片段(函数名经 #define 重命名) + taczfixesCamoDispatch()
 * </pre>
 *
 * 新程序 = 原 gun_pbr 的 vsh/json + 生成后的 fsh, 通过一个只读虚拟 ResourceProvider 交给
 * {@link ShaderInstance} 编译, 不落盘、不注册资源包。
 */
public final class CamoShaderBuilder {
    private static final Logger LOGGER = LogManager.getLogger("taczfixes");
    private static final Gson GSON = new Gson();
    private static final String BASE_SHADER = "gun_pbr";
    private static final String GENERATED_PREFIX = "gun_pbr_camo_";
    private static final String CAMO_BEGIN = "//<<<TACZFIXES_CAMO_BEGIN>>>";
    private static final String CAMO_END = "//<<<TACZFIXES_CAMO_END>>>";
    private static final String FUNCTION_NAME = "taczfixesCamo";

    private static final PackLocationInfo VIRTUAL_LOCATION = new PackLocationInfo(
            "taczfixes_camo", Component.literal("taczfixes dynamic camo"), PackSource.BUILT_IN, Optional.empty());
    private static final PackResources VIRTUAL_PACK = new VirtualPack();
    private static int revision;

    private CamoShaderBuilder() {
    }

    /**
     * 用当前已 stage 的迷彩定义构建程序。
     *
     * @param provider 原版 Shader 重载传入的 ResourceProvider(内含 shaders/ 资源)
     * @return 成功时返回可用的 ShaderInstance 与已排好序的迷彩条目; 无迷彩/失败返回 null
     */
    public static Result build(ResourceProvider provider) {
        List<CamoDefinition> definitions = DynamicCamo.staged();
        if (definitions.isEmpty()) {
            return null;
        }
        String baseFsh = readText(provider, resource("shaders/core/" + BASE_SHADER + ".fsh"));
        String baseVsh = readText(provider, resource("shaders/core/" + BASE_SHADER + ".vsh"));
        String baseJson = readText(provider, resource("shaders/core/" + BASE_SHADER + ".json"));
        if (baseFsh == null || baseVsh == null || baseJson == null) {
            LOGGER.warn("taczfixes: dynamic camo skipped, base gun_pbr shader resources are unavailable");
            return null;
        }
        int begin = baseFsh.indexOf(CAMO_BEGIN);
        int end = baseFsh.indexOf(CAMO_END);
        if (begin < 0 || end < begin) {
            LOGGER.warn("taczfixes: dynamic camo skipped, camo section markers missing in gun_pbr.fsh");
            return null;
        }

        StringBuilder camoBlock = new StringBuilder();
        List<CamoDefinition> valid = new ArrayList<>();
        // 片段文件可以按内容复用: 多条声明指向同一个着色器时只编译一份, 各自传入自己的 params。
        // 这样枪包能用一个着色器通过参数提供多种风格, 也避免同源片段被重复拼接导致函数重定义。
        List<Integer> functionIndices = new ArrayList<>();
        Map<String, Integer> functionsBySource = new LinkedHashMap<>();
        for (CamoDefinition definition : definitions) {
            Integer functionIndex = functionsBySource.get(definition.shaderSource());
            if (functionIndex == null) {
                if (!validate(provider, definition)) {
                    continue;
                }
                int index = functionsBySource.size() + 1;
                camoBlock.append("\n// ---- ").append(definition.id()).append(" ----\n");
                camoBlock.append("#define ").append(FUNCTION_NAME).append(" taczfixesCamo_").append(index).append('\n');
                camoBlock.append(definition.shaderSource()).append('\n');
                camoBlock.append("#undef ").append(FUNCTION_NAME).append('\n');
                functionsBySource.put(definition.shaderSource(), index);
                functionIndex = index;
            }
            valid.add(definition);
            functionIndices.add(functionIndex);
        }
        if (valid.isEmpty()) {
            LOGGER.warn("taczfixes: dynamic camo skipped, every shader snippet failed to compile");
            return null;
        }

        camoBlock.append("\nvec4 ").append(FUNCTION_NAME).append("Dispatch(vec2 uv, float t) {\n");
        for (int i = 0; i < valid.size(); i++) {
            CamoDefinition definition = valid.get(i);
            camoBlock.append("    if (CamoIndex == ").append(i + 1).append(") return taczfixesCamo_")
                    .append(functionIndices.get(i)).append("(uv, t, ")
                    .append(definition.paramLiteral(0)).append(", ")
                    .append(definition.paramLiteral(1)).append(", ")
                    .append(definition.paramLiteral(2)).append(");\n");
        }
        camoBlock.append("    return vec4(0.0);\n}\n");

        String fragment = baseFsh.substring(0, begin) + camoBlock + baseFsh.substring(end + CAMO_END.length());
        int current = ++revision;
        String name = GENERATED_PREFIX + current;
        ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath("taczfixes", name);

        JsonObject root;
        try {
            root = JsonParser.parseString(baseJson).getAsJsonObject();
        } catch (Exception ex) {
            LOGGER.warn("taczfixes: dynamic camo skipped, gun_pbr.json is not valid JSON", ex);
            return null;
        }
        root.addProperty("vertex", "taczfixes:" + name);
        root.addProperty("fragment", "taczfixes:" + name);

        Map<ResourceLocation, byte[]> generated = new LinkedHashMap<>();
        generated.put(resource("shaders/core/" + name + ".json"),
                GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        generated.put(resource("shaders/core/" + name + ".vsh"), baseVsh.getBytes(StandardCharsets.UTF_8));
        generated.put(resource("shaders/core/" + name + ".fsh"), fragment.getBytes(StandardCharsets.UTF_8));

        ResourceProvider generatedProvider = location -> {
            byte[] data = generated.get(location);
            if (data != null) {
                return Optional.of(new Resource(VIRTUAL_PACK, () -> new ByteArrayInputStream(data)));
            }
            return provider.getResource(location);
        };

        ShaderInstance instance;
        try {
            instance = new ShaderInstance(generatedProvider, shaderId, DefaultVertexFormat.NEW_ENTITY);
        } catch (Throwable t) {
            LOGGER.error("taczfixes: dynamic camo program failed to compile ({} camo(s) discarded): {}", valid.size(), t.toString());
            return null;
        }
        List<DynamicCamo.Entry> entries = new ArrayList<>(valid.size());
        for (int i = 0; i < valid.size(); i++) {
            CamoDefinition definition = valid.get(i);
            entries.add(new DynamicCamo.Entry(i + 1, definition.id(), definition.nameKey(),
                    definition.strength(), definition.emissive()));
        }
        LOGGER.info("taczfixes: dynamic camo program built with {} entries ({} rejected snippet(s))",
                entries.size(), definitions.size() - valid.size());
        return new Result(instance, List.copyOf(entries));
    }

    /** 逐条预编译, 坏片段只丢这一条, 不拖垮整批迷彩。 */
    private static boolean validate(ResourceProvider provider, CamoDefinition definition) {
        int id = ++revision;
        String name = "camo_check_" + id;
        String fragment = "#version 150\nout vec4 fragColor;\n"
                + definition.shaderSource() + "\n"
                + "void main() { fragColor = " + FUNCTION_NAME
                + "(vec2(0.5), 0.0, vec4(0.0), vec4(0.0), vec4(0.0)); }\n";
        String vertex = "#version 150\nin vec3 Position;\nvoid main() { gl_Position = vec4(Position, 1.0); }\n";
        JsonObject root = new JsonObject();
        root.addProperty("vertex", "taczfixes:" + name);
        root.addProperty("fragment", "taczfixes:" + name);
        var attributes = new com.google.gson.JsonArray();
        attributes.add("Position");
        root.add("attributes", attributes);
        root.add("samplers", new com.google.gson.JsonArray());
        root.add("uniforms", new com.google.gson.JsonArray());

        Map<ResourceLocation, byte[]> generated = new LinkedHashMap<>();
        generated.put(resource("shaders/core/" + name + ".json"), GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        generated.put(resource("shaders/core/" + name + ".vsh"), vertex.getBytes(StandardCharsets.UTF_8));
        generated.put(resource("shaders/core/" + name + ".fsh"), fragment.getBytes(StandardCharsets.UTF_8));
        ResourceProvider generatedProvider = location -> {
            byte[] data = generated.get(location);
            if (data != null) {
                return Optional.of(new Resource(VIRTUAL_PACK, () -> new ByteArrayInputStream(data)));
            }
            return provider.getResource(location);
        };

        ShaderInstance instance = null;
        try {
            instance = new ShaderInstance(generatedProvider,
                    ResourceLocation.fromNamespaceAndPath("taczfixes", name), DefaultVertexFormat.POSITION);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("taczfixes: camo {} shader snippet rejected: {}", definition.id(), t.getMessage());
            return false;
        } finally {
            if (instance != null) {
                try {
                    instance.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("taczfixes", path);
    }

    private static String readText(ResourceProvider provider, ResourceLocation location) {
        try (Reader reader = provider.openAsReader(location)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException ex) {
            return null;
        }
    }

    /** 构建结果: 可用的程序 + 按 index(1 起) 排好序的迷彩条目。 */
    public record Result(ShaderInstance shader, List<DynamicCamo.Entry> entries) {
    }

    /** 只用来给 {@link Resource} 提供 source() 的占位资源包, 不参与资源树。 */
    private static final class VirtualPack implements PackResources {
        @Override
        public IoSupplier<InputStream> getRootResource(String... paths) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of();
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return VIRTUAL_LOCATION;
        }

        @Override
        public void close() {
        }
    }
}
