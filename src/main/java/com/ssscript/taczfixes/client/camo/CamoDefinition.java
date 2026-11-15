package com.ssscript.taczfixes.client.camo;

import net.minecraft.resources.ResourceLocation;

/**
 * 枪包声明的动态迷彩: data/<ns>/taczfixes/camo/<name>.json 描述, 着色器片段放在枪包 assets 里。
 *
 * @param id           迷彩 id(命名空间 + 文件名, 例如 {@code my_pack:nebula})
 * @param nameKey      显示名(翻译键或纯文本, 客户端按 I18n 解析)
 * @param shaderSource 着色器片段源码(已读入内存, 拼接进 gun_pbr 程序)
 * @param strength     默认混合强度 0..1
 * @param emissive     自发光倾向 0..1
 * @param params       最多 3 个 vec4 参数, 由片段里的 p0/p1/p2 接收
 */
public record CamoDefinition(ResourceLocation id, String nameKey, String shaderSource,
                             float strength, float emissive, float[][] params) {

    /** 单条定义最多 3 个 vec4 参数。 */
    public static final int MAX_PARAMS = 3;

    public CamoDefinition {
        params = normalize(params);
    }

    private static float[][] normalize(float[][] raw) {
        float[][] out = new float[MAX_PARAMS][4];
        if (raw != null) {
            for (int row = 0; row < Math.min(raw.length, MAX_PARAMS); row++) {
                float[] src = raw[row];
                if (src == null) {
                    continue;
                }
                for (int col = 0; col < Math.min(src.length, 4); col++) {
                    out[row][col] = src[col];
                }
            }
        }
        return out;
    }

    /** 参数按 vec4 字面量输出, 供生成的 dispatch 直接调用。 */
    public String paramLiteral(int index) {
        float[] v = params[index];
        return "vec4(" + num(v[0]) + ", " + num(v[1]) + ", " + num(v[2]) + ", " + num(v[3]) + ")";
    }

    private static String num(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "0.0";
        }
        String text = Float.toString(value);
        return text.indexOf('.') >= 0 ? text : text + ".0";
    }
}
