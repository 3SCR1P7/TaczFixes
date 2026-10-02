package com.ssscript.taczfixes.client.render;

import com.ssscript.taczfixes.TaczFixesMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    private static String ChunkInjection(String s) {
        s = s.replace("void main()", getShimmerSource() + "void main()");
        return new StringBuffer(s).insert(s.lastIndexOf('}'),
                "vertexColor = color_light_uv(pos, vertexColor,UV2);\n"
        ).toString();
    }

    private static String PositionInjection(String s) {
        s = s.replace("void main()", getShimmerSource() + "void main()");
        return new StringBuffer(s).insert(s.lastIndexOf('}'),
                "vertexColor = color_light_uv(Position, vertexColor,UV2);\n"
        ).toString();
    }

    private static String EntityInjectionLightMapColor(String s) {
        s = s.replace("void main()", getShimmerSource() + "void main()");
        return new StringBuffer(s).insert(s.lastIndexOf('}'), "lightMapColor = color_light(IViewRotMat * Position, lightMapColor);\n").toString();
    }

    private static String EntityInjectionVertexColor(String s) {
        s = s.replace("void main()", getShimmerSource() + "void main()");
        return new StringBuffer(s).insert(s.lastIndexOf('}'), "vertexColor = color_light(IViewRotMat * Position, vertexColor);\n").toString();
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
        s = new StringBuffer(s).insert(s.lastIndexOf("void main()"), getLightShader()).toString();
        s = new StringBuffer(s).insert(s.lastIndexOf('}'),
                "v_Color = color_light_uv(position, v_Color, _vert_tex_light_coord).rgba;\n"
        ).toString();
        return s;
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

    public FloatBuffer getBuffer() {
        return BUFFER;
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

    public void reloadShaders() {
        if (lightUBO == null) {
            int size = getOffset(MAXIMUM_LIGHT_SUPPORT);
            int uboOffset = ModList.get().isLoaded("modernui") ? 6 : 1;
            lightUBO = new ShaderUBO();
            lightUBO.createBufferData(size, GL30.GL_STREAM_DRAW);
            envUBO = new ShaderUBO();
            envUBO.createBufferData(32, GL30.GL_STREAM_DRAW);
            envUBO.bufferSubData(0, new int[8]);
            lightUBO.blockBinding(uboOffset);
            envUBO.blockBinding(uboOffset + 1);
        }
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
