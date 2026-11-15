package com.ssscript.taczfixes.client.camo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
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
 *   公共工具函数 + 每条迷彩片段(函数名经 #define 重命名) + taczfixesCamoDispatch()
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

    private static final PackResources VIRTUAL_PACK = new VirtualPack();
    private static int revision;

    private CamoShaderBuilder() {
    }

    /**
     * 片段可直接调用的公共工具(名称以 tf 开头, 枪包片段不要重复定义)。
     * 噪声/域扭曲/星点/星图/相位这组函数与 COD Dynamic Camouflage 的 dgs* 实现同源
     * (MIT 之外的口头授权: 作者允许参考复用), 只是改名成 tf 前缀并统一收纳在这里,
     * 这样多个迷彩片段共用同一份定义, 不会因为重复定义函数而整批编译失败。
     */
    private static final String HELPERS = """
            // ===== taczfixes 动态迷彩公共工具 =====
            // 哈希用无 sin 的整数混色写法(Dave Hoskins 风格): 视觉统计一致, 但比 sin 版快很多,
            // fbm/星点每像素要调十几次哈希, 这是迷彩最划算的一处优化。
            float tfSat(float x) { return clamp(x, 0.0, 1.0); }
            float tfHash11(float x) {
                x = fract(x * 0.1031);
                x *= x + 33.33;
                x *= x + x;
                return fract(x);
            }
            float tfHash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            vec2 tfHash22(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.xx + p3.yz) * p3.zy);
            }
            // 旧版片段用的别名, 保留兼容
            float tfHash21(vec2 p) { return tfHash12(p); }
            mat2 tfRot(float a) { float c = cos(a); float s = sin(a); return mat2(c, -s, s, c); }
            float tfNoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                float a = tfHash12(i);
                float b = tfHash12(i + vec2(1.0, 0.0));
                float c = tfHash12(i + vec2(0.0, 1.0));
                float d = tfHash12(i + vec2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }
            float tfFbm(vec2 p) {
                float s = 0.0;
                float a = 0.5;
                for (int i = 0; i < 4; i++) {
                    s += a * tfNoise(p);
                    p = p * 2.03 + vec2(1.7, 9.2);
                    a *= 0.5;
                }
                return s;
            }
            float tfFbm3(vec2 p) {
                float s = 0.0;
                float a = 0.5;
                for (int i = 0; i < 3; i++) {
                    s += a * tfNoise(p);
                    p = p * 2.07 + vec2(4.3, 2.1);
                    a *= 0.5;
                }
                return s / 0.875;
            }
            // 域扭曲: 两股反向流动的噪声把采样网格揉皱, 图案才会弯折/分叉/打旋
            vec2 tfWarp(vec2 q, float t, float amt) {
                float w1 = tfFbm(q * 1.25 - vec2(t * 0.170, t * 0.045));
                float w2 = tfFbm(q * 1.25 + vec2(t * 0.105, -t * 0.130) + 5.2);
                return q + (vec2(w1, w2) - 0.5) * amt;
            }
            // 明暗潮: 快慢两条反向行波叠加, 均值归一 —— 只改对比, 不改整体亮度
            float tfSwell(float g, float t, float depth) {
                const float TAU = 6.2831853;
                float a = 0.5 + 0.5 * sin(TAU * (g * 1.70 - t * 0.31));
                float b = 0.5 + 0.5 * sin(TAU * (g * 0.53 + t * 0.17));
                return 1.0 + depth * (0.72 * a + 0.28 * b - 0.42);
            }
            // 星点: 格点高斯核, 半径带 "≥0.9 屏幕像素" 下限, 缩小到看不清时整层渐隐
            vec3 tfStars(vec2 uv, float scale, float t, float thresh) {
                vec2 p = uv * scale;
                float cellPx = 1.0 / max(max(fwidth(p.x), fwidth(p.y)), 1e-5);
                vec2 g = floor(p);
                vec2 f = fract(p);
                float exist = tfHash11(dot(g, vec2(3.71, 9.17)) + 31.7);
                if (exist < thresh) {
                    return vec3(0.0);
                }
                vec2 h = tfHash22(g);
                vec2 pos = vec2(0.16, 0.16) + 0.68 * vec2(h.x, h.y);
                vec2 d = f - pos;
                float rr = max(0.030 + 0.012 * fract(h.x * 4.7), 1.37 / max(cellPx, 1.0));
                float core = exp(-dot(d, d) / (rr * rr * 0.62));
                float mag = smoothstep(thresh, 1.0, exist);
                float tw = 0.80 + 0.20 * sin(t * (0.9 + 2.4 * fract(h.y * 5.3)) + 6.2831853 * h.x);
                vec2 cs = tfHash22(g + 77.7);
                vec3 col = mix(vec3(0.72, 0.83, 1.00), vec3(1.00, 0.97, 0.88), smoothstep(0.25, 0.75, cs.x));
                col = mix(col, vec3(1.00, 0.68, 0.52), smoothstep(0.86, 1.00, cs.y));
                float crowd = smoothstep(2.5, 6.0, cellPx);
                return core * (0.35 + 0.65 * tw) * (0.72 + 0.28 * mag) * 2.35 * col * crowd;
            }
            // 多层星空(带视差漂移): vel = 图集宽/秒
            vec3 tfStarfield(vec2 uv, float t, vec2 vel) {
                vec2 d1 = vel * t * 1.00;
                vec2 d2 = vel * t * vec2(0.74, 0.86) + vec2(0.0, vel.x * t * 0.10);
                vec2 d3 = vel * t * vec2(0.52, 0.60) - vec2(vel.y * t * 0.12, 0.0);
                // 3 层就够撑起视差星空(原 4 层里最暗那层几乎看不见), 每像素省一层哈希+高斯
                return tfStars(uv - d1, 52.0, t, 0.52)
                     + tfStars((uv - d2) * 1.85 + 4.3, 52.0, t * 1.25 + 7.0, 0.50) * 0.85
                     + tfStars((uv - d3) * 3.27 - 9.1, 52.0, t * 1.6 + 2.5, 0.46) * 0.60;
            }
            vec3 tfStarfield(vec2 uv, float t) { return tfStarfield(uv, t, vec2(0.0)); }
            vec2 tfNodePos(vec2 cell) {
                vec2 h = tfHash22(cell * 1.37 + 5.7);
                return cell + 0.5 + (h - 0.5) * 0.66;
            }
            float tfSegDist(vec2 p, vec2 a, vec2 b) {
                vec2 pa = p - a;
                vec2 ba = b - a;
                float hh = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
                return length(pa - ba * hh);
            }
            // 星座星图: 格点亮星 + 笔直细连线(稀疏图形, 跨面断层也不明显)
            vec3 tfStarChart(vec2 uv, float t, float scale, vec3 lineCol, vec3 starCol) {
                vec2 p = uv * scale;
                float nodePx = 1.0 / max(max(fwidth(p.x), fwidth(p.y)), 1e-5);
                vec2 g = floor(p);
                vec3 acc = vec3(0.0);
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        vec2 n = g - 1.0 + vec2(float(i), float(j));
                        vec2 a = tfNodePos(n);
                        float da = length(p - a);
                        float mag = tfHash11(dot(n, vec2(7.31, 2.17)));
                        float tw = 0.85 + 0.15 * sin(t * (0.8 + 1.7 * mag) + 6.2831853 * mag);
                        float nr = max(0.037, 1.37 / max(nodePx, 1.0));
                        acc += starCol * exp(-da * da / (nr * nr * 0.62)) * tw * (0.85 + 0.45 * mag) * 1.55;
                        for (int k = 0; k < 3; k++) {
                            vec2 dir = k == 0 ? vec2(1.0, 0.0) : (k == 1 ? vec2(0.0, 1.0) : vec2(1.0, 1.0));
                            float gate = tfHash11(dot(n, vec2(3.13, 5.91)) + float(k) * 17.71);
                            if (gate < 0.46) {
                                continue;
                            }
                            float d = tfSegDist(p, a, tfNodePos(n + dir));
                            acc += lineCol * (exp(-d * d / 0.00028) + 0.16 * exp(-d * d / 0.0032))
                                 * (0.55 + 0.45 * gate) * tw;
                        }
                    }
                }
                return acc;
            }
            // 三相位权重(诞生/扩散/消亡循环): 窗口 0.030..0.300
            float tfPhaseW(float cyc, float center) {
                float d = abs(fract(cyc - center + 0.5) - 0.5);
                return 1.0 - smoothstep(0.030, 0.300, d);
            }
            // 两相位交界处的"激波中间色": 防止补色各半混成死灰
            vec3 tfBlendHue(float mAB, float mBC, float mCA) {
                float msum = max(mAB + mBC + mCA, 1e-4);
                return (mAB * vec3(1.000, 0.330, 0.520)
                      + mBC * vec3(0.330, 0.800, 1.000)
                      + mCA * vec3(0.880, 0.970, 0.420)) / msum;
            }
            vec3 tfHsv(float h, float s, float v) {
                vec3 rgb = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
                return v * mix(vec3(1.0), rgb, s);
            }
            """;

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

        StringBuilder camoBlock = new StringBuilder(HELPERS);
        List<CamoDefinition> valid = new ArrayList<>();
        for (CamoDefinition definition : definitions) {
            if (!validate(provider, definition)) {
                continue;
            }
            int index = valid.size() + 1;
            camoBlock.append("\n// ---- ").append(definition.id()).append(" ----\n");
            camoBlock.append("#define ").append(FUNCTION_NAME).append(" taczfixesCamo_").append(index).append('\n');
            camoBlock.append(definition.shaderSource()).append('\n');
            camoBlock.append("#undef ").append(FUNCTION_NAME).append('\n');
            valid.add(definition);
        }
        if (valid.isEmpty()) {
            LOGGER.warn("taczfixes: dynamic camo skipped, every shader snippet failed to compile");
            return null;
        }

        camoBlock.append("\nvec4 ").append(FUNCTION_NAME).append("Dispatch(vec2 uv, float t) {\n");
        for (int i = 0; i < valid.size(); i++) {
            CamoDefinition definition = valid.get(i);
            camoBlock.append("    if (CamoIndex == ").append(i + 1).append(") return taczfixesCamo_")
                    .append(i + 1).append("(uv, t, ")
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
        String fragment = "#version 150\nout vec4 fragColor;\n" + HELPERS + "\n"
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
        public String packId() {
            return "taczfixes_camo";
        }

        @Override
        public void close() {
        }
    }
}
