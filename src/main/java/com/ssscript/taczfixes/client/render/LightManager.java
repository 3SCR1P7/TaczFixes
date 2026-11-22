package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 彩色光管理 + 着色器注入。移植自 Shimmer (MIT, https://github.com/Low-Drag-MC/Shimmer),
 * 仅保留动态彩色点光部分(去掉配置/方块光/泛光/Iris 兼容)。
 */
public enum LightManager {
    INSTANCE;
    private static final int MAXIMUM_LIGHT_SUPPORT = 2048;
    private final List<ColorPointLight> UV_LIGHT = new ArrayList<>(MAXIMUM_LIGHT_SUPPORT);
    private final List<ColorPointLight> NO_UV_LIGHT = new ArrayList<>(MAXIMUM_LIGHT_SUPPORT);
    private final FloatBuffer BUFFER = BufferUtils.createFloatBuffer(MAXIMUM_LIGHT_SUPPORT * ColorPointLight.STRUCT_SIZE);

    ShaderUBO lightUBO;
    ShaderUBO envUBO;

    private static String getShimmerSource() {
        return "\n" + getLightShader() + "\n\n";
    }

    private static final Pattern MAIN_DEFINITION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern WRAPPED_MAIN_CALL = Pattern.compile("\\b([A-Za-z_]\\w*_main)\\s*\\(\\s*(?:void\\s*)?\\)\\s*;");

    private static String ChunkInjection(String s) {
        return inject(s, "vertexColor = color_light_uv(pos, vertexColor,UV2);\n");
    }

    private static String PositionInjection(String s) {
        return inject(s, "vertexColor = color_light_uv(Position, vertexColor,UV2);\n");
    }

    private static String EntityInjectionLightMapColor(String s) {
        return inject(s, "lightMapColor = color_light((transpose(mat3(ModelViewMat)) * Position), lightMapColor);\n");
    }

    private static String EntityInjectionVertexColor(String s) {
        return inject(s, "vertexColor = color_light((transpose(mat3(ModelViewMat)) * Position), vertexColor);\n");
    }

    /** 在着色器真正的 main 函数体末尾插入语句, 并把彩色光函数定义插到该函数之前。
     *  别的 mod(如 TaCZ: Scope Overhaul 的 INLINE 模式)会把 main 改名成 xxx_main 并在末尾包一层新的 main,
     *  这时必须插进被调用的内层函数, 语句才能看到 main 里的局部变量。
     *  找不到可插入的 main 时原样返回, 由调用方(试编译校验)决定是否使用。 */
    private static String inject(String source, String statement) {
        int[] target = findInjectionTarget(source);
        if (target == null) {
            return source;
        }
        return new StringBuilder(source)
                .insert(target[1], statement)
                .insert(target[0], getShimmerSource())
                .toString();
    }

    /** @return [函数定义起始位置, 函数体右花括号位置] */
    private static int[] findInjectionTarget(String source) {
        Matcher main = MAIN_DEFINITION.matcher(source);
        int mainStart = -1;
        int mainEnd = -1;
        while (main.find()) {
            mainStart = main.start();
            mainEnd = main.end();
        }
        if (mainStart < 0) {
            return null;
        }
        int mainOpen = source.indexOf('{', mainEnd);
        if (mainOpen < 0) {
            return null;
        }
        int mainClose = findMatchingBrace(source, mainOpen);
        if (mainClose < 0) {
            return null;
        }
        Matcher wrapped = WRAPPED_MAIN_CALL.matcher(source.substring(mainOpen + 1, mainClose));
        if (wrapped.find()) {
            int bodyStart = findFunctionDefinition(source, wrapped.group(1), mainStart);
            if (bodyStart >= 0) {
                int bodyOpen = source.indexOf('{', bodyStart);
                int bodyClose = bodyOpen < 0 ? -1 : findMatchingBrace(source, bodyOpen);
                if (bodyClose >= 0) {
                    return new int[]{bodyStart, bodyClose};
                }
            }
        }
        return new int[]{mainStart, mainClose};
    }

    private static int findFunctionDefinition(String source, String name, int before) {
        Matcher definition = Pattern.compile("\\bvoid\\s+" + Pattern.quote(name) + "\\s*\\(").matcher(source);
        int found = -1;
        while (definition.find() && definition.start() < before) {
            found = definition.start();
        }
        return found;
    }

    private static int findMatchingBrace(String source, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    i = source.indexOf('\n', i + 2);
                    if (i < 0) {
                        return -1;
                    }
                } else if (next == '*') {
                    i = source.indexOf("*/", i + 2);
                    if (i < 0) {
                        return -1;
                    }
                    i++;
                }
            } else if (c == '"') {
                i++;
                while (i < source.length() && source.charAt(i) != '"') {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String lightShader;

    private static String getLightShader() {
        if (lightShader == null) {
            try (InputStream stream = LightManager.class.getResourceAsStream("/assets/taczfixes/shaders/core/shimmer.glsl")) {
                if (stream == null) {
                    return "";
                }
                lightShader = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("#version 150", "");
            } catch (IOException e) {
                TaczFixesMod.LOGGER.error("error while loading colored light shader", e);
                lightShader = "";
            }
        }
        return lightShader;
    }

    /** Embeddium(钠系)地形着色器注入: 顶点阶段按世界坐标叠加彩色光。 */
    public static String embeddiumVVSHInjection(String s) {
        return inject(s, "v_Color = color_light_uv(position, v_Color, _vert_tex_light_coord).rgba;\n");
    }

    /** Sodium(1.21.1) 地形着色器注入: Sodium 的 light coord 是 0..1 的 vec2。 */
    public static String sodiumVVSHInjection(String s) {
        return inject(s, "v_Color = color_light_uv(position, v_Color, ivec2(_vert_tex_light_coord * 255.0)).rgba;\n");
    }

    public static void injectShaders() {
        ShaderInjection.registerVSHInjection("particle", LightManager::PositionInjection);
        ShaderInjection.registerVSHInjection("rendertype_solid", LightManager::ChunkInjection);
        ShaderInjection.registerVSHInjection("rendertype_cutout", LightManager::ChunkInjection);
        ShaderInjection.registerVSHInjection("rendertype_cutout_mipped", LightManager::ChunkInjection);
        ShaderInjection.registerVSHInjection("rendertype_translucent", LightManager::ChunkInjection);
        ShaderInjection.registerVSHInjection("rendertype_armor_cutout_no_cull", LightManager::PositionInjection);
        ShaderInjection.registerVSHInjection("rendertype_entity_cutout", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_cutout_no_cull", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_cutout_no_cull_z_offset", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_decal", LightManager::EntityInjectionVertexColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_no_outline", LightManager::EntityInjectionVertexColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_smooth_cutout", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_solid", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_translucent", LightManager::EntityInjectionLightMapColor);
        ShaderInjection.registerVSHInjection("rendertype_entity_translucent_cull", LightManager::EntityInjectionVertexColor);
    }

    public static void clear() {
        for (ColorPointLight light : INSTANCE.UV_LIGHT) {
            light.lightManager = null;
        }
        for (ColorPointLight light : INSTANCE.NO_UV_LIGHT) {
            light.lightManager = null;
        }
        INSTANCE.UV_LIGHT.clear();
        INSTANCE.NO_UV_LIGHT.clear();
    }

    public int maxFixedLight() {
        return UV_LIGHT.size() + NO_UV_LIGHT.size();
    }

    public void renderLevelPre(int blockLightSize, float camX, float camY, float camZ) {
        if (lightUBO == null || envUBO == null) {
            return;
        }
        updateNoUVLight();
        lightUBO.bufferSubData(getOffset(UV_LIGHT.size()), BUFFER);
        envUBO.bufferSubData(0, new int[]{UV_LIGHT.size() + blockLightSize});
        envUBO.bufferSubData(4, new int[]{NO_UV_LIGHT_COUNT});
        envUBO.bufferSubData(16, new float[]{camX, camY, camZ});
    }

    private int NO_UV_LIGHT_COUNT = 0;

    public void updateNoUVLight() {
        NO_UV_LIGHT_COUNT = 0;
        BUFFER.clear();
        for (ColorPointLight light : NO_UV_LIGHT) {
            if (light.enable) {
                light.uploadBuffer(BUFFER);
                NO_UV_LIGHT_COUNT++;
            }
        }
        BUFFER.flip();
    }

    public void renderLevelPost() {
        if (envUBO != null) {
            envUBO.bufferSubData(0, new int[8]);
        }
    }

    private void ensureBuffers() {
        if (lightUBO != null) {
            return;
        }
        int size = getOffset(MAXIMUM_LIGHT_SUPPORT);
        int uboOffset = 14;
        lightUBO = new ShaderUBO();
        lightUBO.createBufferData(size, GL30.GL_STREAM_DRAW);
        envUBO = new ShaderUBO();
        envUBO.createBufferData(32, GL30.GL_STREAM_DRAW);
        envUBO.bufferSubData(0, new int[8]);
        lightUBO.blockBinding(uboOffset);
        envUBO.blockBinding(uboOffset + 1);
    }

    public void reloadShaders() {
        ensureBuffers();
        bindProgram("particle");
        bindProgram("rendertype_solid");
        bindProgram("rendertype_cutout");
        bindProgram("rendertype_cutout_mipped");
        bindProgram("rendertype_translucent");
        bindProgram("rendertype_armor_cutout_no_cull");
        bindProgram("rendertype_entity_cutout");
        bindProgram("rendertype_entity_cutout_no_cull");
        bindProgram("rendertype_entity_cutout_no_cull_z_offset");
        bindProgram("rendertype_entity_decal");
        bindProgram("rendertype_entity_no_outline");
        bindProgram("rendertype_entity_smooth_cutout");
        bindProgram("rendertype_entity_solid");
        bindProgram("rendertype_entity_translucent");
        bindProgram("rendertype_entity_translucent_cull");
    }

    private void bindProgram(String shaderName) {
        var instance = Minecraft.getInstance().gameRenderer.getShader(shaderName);
        if (instance != null) {
            lightUBO.bindToShader(instance.getId(), "Lights");
            envUBO.bindToShader(instance.getId(), "Env");
        }
    }

    /** 绑定彩色光 UBO 到外部程序(Embeddium 地形着色器)。 */
    public void bindRbProgram(int programID) {
        ensureBuffers();
        if (lightUBO == null || envUBO == null) {
            return;
        }
        lightUBO.bindToShader(programID, "Lights");
        envUBO.bindToShader(programID, "Env");
    }

    public ColorPointLight addLight(Vector3f pos, int color, float radius, boolean uv) {
        if (maxFixedLight() == MAXIMUM_LIGHT_SUPPORT) {
            return null;
        }
        ColorPointLight light = new ColorPointLight(this, pos, color, radius, uv ? getOffset(UV_LIGHT.size()) : -1, uv);
        if (uv) {
            UV_LIGHT.add(light);
            lightUBO.bufferSubData(light.offset, light.getData());
        } else {
            NO_UV_LIGHT.add(light);
        }
        return light;
    }

    public ColorPointLight addLight(Vector3f pos, int color, float radius) {
        return addLight(pos, color, radius, false);
    }

    private int getOffset(int index) {
        return (index * ColorPointLight.STRUCT_SIZE) << 2;
    }

    void bufferSubData(int offset, float[] data) {
        if (lightUBO != null) {
            lightUBO.bufferSubData(offset, data);
        }
    }

    void removeLight(ColorPointLight removed) {
        if (removed.uv) {
            int index = UV_LIGHT.indexOf(removed);
            if (index >= 0) {
                for (int i = index + 1; i < UV_LIGHT.size(); i++) {
                    UV_LIGHT.get(i).offset = getOffset(i - 1);
                }
                UV_LIGHT.remove(index);
                if (index < UV_LIGHT.size()) {
                    Minecraft.getInstance().execute(() -> {
                        BUFFER.clear();
                        for (int i = index; i < UV_LIGHT.size(); i++) {
                            UV_LIGHT.get(i).uploadBuffer(BUFFER);
                        }
                        BUFFER.flip();
                        lightUBO.bufferSubData(getOffset(index), BUFFER);
                    });
                }
            }
        } else {
            NO_UV_LIGHT.remove(removed);
        }
    }
}
