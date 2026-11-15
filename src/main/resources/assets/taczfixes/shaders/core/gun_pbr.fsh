#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat4 ProjMat;
uniform int HasNormal;
uniform int HasSpecular;
uniform int FallbackMaterial;
uniform int EmissionPass;
uniform int ScopeDivisionPass;
uniform int GlareDepthTest;
uniform float ReflectionStrength;
uniform float GlareStrength;
uniform float EmissionStrength;
uniform float DefaultRoughness;
uniform float DefaultMetal;
uniform float DefaultReflection;
uniform float EnvStrength;
uniform float Anisotropy;
uniform float WetDetail;
uniform float SSRStrength;
uniform float Time;
// 动态迷彩: 由 PbrRenderer 按当前渲染物品的 NBT 逐次绘制写入。
//   CamoIndex    = 当前迷彩序号(0=无), 对应生成程序里的 dispatch 分支
//   CamoStrength = 该迷彩定义的混合强度(0..1)
//   CamoEmissive = 该迷彩定义的自发光倾向(0..1), 亮带同时进泛光
uniform int CamoIndex;
uniform float CamoStrength;
uniform float CamoEmissive;
uniform vec3 CameraPos;
uniform vec3 ViewRotX;
uniform vec3 ViewRotY;
uniform vec3 ViewRotZ;
uniform vec3 CelestialDirection;
uniform vec3 CelestialColor;
uniform vec3 SkyZenith;
uniform vec3 SkyHorizon;
uniform vec3 SunHorizon;
uniform vec2 SunAzimuth;
uniform float SkyAmbient;
uniform float CelestialVisibility;
uniform float SunOcclusion;
uniform float LocalSkyExposure;
uniform float Wetness;
uniform vec3 LocalLightPos1;
uniform vec3 LocalLightColor1;
uniform float LocalLightStrength1;
uniform vec3 LocalLightPos2;
uniform vec3 LocalLightColor2;
uniform float LocalLightStrength2;
uniform vec3 LocalLightPos3;
uniform vec3 LocalLightColor3;
uniform float LocalLightStrength3;
uniform vec3 LocalLightPos4;
uniform vec3 LocalLightColor4;
uniform float LocalLightStrength4;

in float vertexDistance;
in vec4 vertexColor;
in vec4 rawVertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in float skyExposure;
in vec3 viewPosition;
in vec3 viewNormal;
flat in int illuminatedGroup;
out vec4 fragColor;

// 相机朝向(世界->视角空间), 由 CPU 传入。不能从 ModelViewMat 提取:
// 手持枪械的 ModelViewMat 还包含枪械模型自身的旋转, 会让光照方向整体偏转。
mat3 worldToView() { return mat3(ViewRotX, ViewRotY, ViewRotZ); }
vec3 fragWorldPos() { return CameraPos + transpose(worldToView()) * viewPosition; }

vec3 safeNormalize(vec3 v) { return v * inversesqrt(max(dot(v, v), 1e-8)); }

vec3 mappedNormal(vec3 n, vec2 xy) {
    vec3 dp1 = dFdx(viewPosition), dp2 = dFdy(viewPosition);
    vec2 duv1 = dFdx(texCoord0), duv2 = dFdy(texCoord0);
    vec3 p2 = cross(dp2, n), p1 = cross(n, dp1);
    vec3 t = p2 * duv1.x + p1 * duv2.x;
    vec3 b = p2 * duv1.y + p1 * duv2.y;
    float scale = inversesqrt(max(max(dot(t, t), dot(b, b)), 1e-12));
    vec3 tangentNormal = vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0)));
    return safeNormalize(mat3(t * scale, b * scale, n) * tangentNormal);
}

vec3 metalF0(float code, vec3 albedo) {
    if (code < 230.5) return vec3(0.56, 0.57, 0.58); // iron
    if (code < 231.5) return vec3(1.00, 0.77, 0.34); // gold
    if (code < 232.5) return vec3(0.91, 0.92, 0.92); // aluminium
    if (code < 233.5) return vec3(0.55, 0.56, 0.55); // chrome
    if (code < 234.5) return vec3(0.66, 0.61, 0.53); // copper alloy
    if (code < 235.5) return vec3(0.95, 0.64, 0.54); // copper
    if (code < 236.5) return vec3(0.67, 0.64, 0.59); // platinum
    if (code < 237.5) return vec3(0.95, 0.93, 0.88); // silver
    return albedo;
}

// 由 UV 导数构造切线基, 供各向异性高光使用(拉丝金属沿 U 方向拉长)。
void tangentBasis(vec3 n, out vec3 t, out vec3 b) {
    vec3 dp1 = dFdx(viewPosition), dp2 = dFdy(viewPosition);
    vec2 duv1 = dFdx(texCoord0), duv2 = dFdy(texCoord0);
    vec3 tRaw = dp1 * duv2.y - dp2 * duv1.y;
    float len = length(tRaw);
    if (len < 1e-6) {
        vec3 fallback = abs(n.y) > 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        t = safeNormalize(cross(fallback, n));
    } else {
        t = tRaw / len;
    }
    b = safeNormalize(cross(n, t));
    t = safeNormalize(cross(b, n));
}

// 各向异性 GGX 直接高光。aniso 为 0 时退化为普通 GGX。
// wrap > 0 时对 n·l 做包裹, 让侧对光源的面也能分到一部分高光(游戏化处理)。
vec3 specGGX(vec3 n, vec3 v, vec3 t, vec3 b, vec3 l, vec3 lightColor, float intensity,
             vec3 f0, float roughness, float aniso, float wrap) {
    float nl = max((dot(n, l) + wrap) / (1.0 + wrap), 0.0);
    if (nl <= 0.0) return vec3(0.0);
    float nv = max(dot(n, v), 0.001);
    vec3 h = safeNormalize(v + l);
    float nh = max(dot(n, h), 0.0);
    float vh = max(dot(v, h), 0.0);
    float a = max(roughness * roughness, 1e-4);
    float at = max(a * (1.0 + aniso), 1e-3);
    float ab = max(a * (1.0 - aniso), 1e-3);
    float ht = dot(t, h) / at;
    float hb = dot(b, h) / ab;
    float denom = ht * ht + hb * hb + nh * nh;
    float d = 1.0 / max(3.14159265 * at * ab * denom * denom, 1e-6);
    float k = pow(roughness + 1.0, 2.0) / 8.0;
    float g = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
    vec3 f = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    return f * d * g / max(4.0 * nv, 0.001) * lightColor * intensity;
}

// 环境色: 地平线用当前维度雾色(自动含天气/群系), 天顶用真实天空色, 向下取雾色压暗的地面回弹。
vec3 skyEnvironment(vec3 dirWorld) {
    vec3 horizon = mix(FogColor.rgb, SkyHorizon, 0.30);
    vec3 ground = FogColor.rgb * 0.32 + vec3(0.020, 0.018, 0.015);
    vec3 col = dirWorld.y >= 0.0
            ? mix(horizon, SkyZenith, pow(clamp(dirWorld.y, 0.0, 1.0), 0.72))
            : mix(horizon, ground, pow(clamp(-dirWorld.y, 0.0, 1.0), 0.55));
    vec3 flatDir = safeNormalize(vec3(dirWorld.x, 0.0, dirWorld.z) + vec3(1e-5));
    vec3 sunFlat = safeNormalize(vec3(SunAzimuth.x, 0.0, SunAzimuth.y));
    float sunSide = clamp(dot(flatDir, sunFlat) * 0.5 + 0.5, 0.0, 1.0);
    float horizonBand = exp(-abs(dirWorld.y) * 3.5);
    col += SunHorizon * horizonBand * (0.30 + 0.70 * pow(sunSide, 2.5));
    // 室内/洞穴按天空可见度压暗(火把色由局部光项补)。
    col = mix(col * 0.22, col, clamp(LocalSkyExposure * 0.85 + 0.15, 0.0, 1.0));
    return col;
}

// 屏幕空间反射 + 屏幕探针兜底。
// 深度步进未命中时, 把反射方向投影到屏幕采样场景色(越界钳制并软化),
// 保证金属表面始终有场景倒影, 而不是没命中就一片平。
vec4 screenSpaceReflection(vec3 n, vec3 envTint) {
    vec4 result = vec4(0.0);
    if (SSRStrength <= 0.001) return result;
    vec3 Rv = reflect(normalize(viewPosition), n);
    float front = -Rv.z;
    if (front <= 0.05) return result;
    vec3 pos = viewPosition + n * 0.05;
    float stepLen = max(length(viewPosition) * 0.015, 0.2);
    for (int i = 0; i < 24; i++) {
        pos += Rv * stepLen;
        stepLen *= 1.24;
        vec4 clip = ProjMat * vec4(pos, 1.0);
        if (clip.w <= 0.0) break;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
        float sceneDepth = texture(Sampler7, uv).r;
        float candidateDepth = clip.z / clip.w * 0.5 + 0.5;
        float thickness = candidateDepth - sceneDepth;
        if (thickness > 0.0015 && thickness < 0.06) {
            float edge = smoothstep(0.0, 0.05, uv.x) * smoothstep(0.0, 0.05, 1.0 - uv.x)
                    * smoothstep(0.0, 0.05, uv.y) * smoothstep(0.0, 0.05, 1.0 - uv.y);
            float fade = 1.0 - clamp(thickness / 0.06, 0.0, 1.0);
            return vec4(texture(Sampler6, uv).rgb, edge * fade);
        }
    }
    // 探针兜底: 透视投影反射方向到屏幕。
    if (abs(ProjMat[2][3]) < 0.5) return result;
    vec2 pin = vec2(ProjMat[0][0], ProjMat[1][1]);
    float depth = max(abs(front), 0.10);
    vec2 ndc = vec2(Rv.x * pin.x, Rv.y * pin.y) / depth;
    vec2 puv = ndc * 0.5 + 0.5;
    vec2 lo = vec2(0.010, 0.014);
    vec2 hi = vec2(0.990, 0.986);
    vec2 offv = max(max(lo - puv, puv - hi), vec2(0.0));
    float off = offv.x + offv.y;
    vec2 cuv = clamp(puv, lo, hi);
    float give = 0.075 * smoothstep(0.0, 0.45, off);
    vec2 inward = step(puv, lo) - step(hi, puv);
    cuv += inward * give;
    float inside = 1.0 - smoothstep(0.0, 0.30, off);
    float facing = 0.62 + 0.38 * smoothstep(0.0, 0.22, abs(front));
    float rad = 0.012 * (1.0 + 1.2 * clamp(off, 0.0, 1.0));
    vec2 ox = vec2(rad, 0.0);
    vec2 oy = vec2(0.0, rad);
    vec3 probeCol = texture(Sampler6, cuv).rgb * 0.36
            + (texture(Sampler6, cuv + ox).rgb + texture(Sampler6, cuv - ox).rgb
             + texture(Sampler6, cuv + oy).rgb + texture(Sampler6, cuv - oy).rgb) * 0.16;
    float pL = dot(probeCol, vec3(0.2126, 0.7152, 0.0722));
    vec3 pHue = clamp(probeCol / max(pL, 1e-3), vec3(0.0), vec3(1.6));
    float pB = clamp(pL * 1.35, 0.0, 1.55);
    vec3 pRefl = pB * mix(envTint, pHue, 0.55) * 1.05;
    float pMax = max(pRefl.r, max(pRefl.g, pRefl.b));
    pRefl *= min(1.0, 1.05 / max(pMax, 1e-3));
    return vec4(pRefl, 0.35 * inside * facing);
}

vec3 localAmbientSum() {
    return LocalLightColor1 * LocalLightStrength1 + LocalLightColor2 * LocalLightStrength2
            + LocalLightColor3 * LocalLightStrength3 + LocalLightColor4 * LocalLightStrength4;
}

// 眩光: 比主反光更宽的高光叶, 写入泛光目标后由泛光链路模糊成光晕。
vec3 glareHighlight(vec3 n, vec3 v, float roughness, vec3 f0, vec3 albedo) {
    float nv = max(dot(n, v), 0.001);
    float glareRoughness = max(roughness, 0.12);
    float wet = clamp(Wetness, 0.0, 1.0);
    float weatherGain = mix(1.6, 0.45, wet);
    float gr = clamp(glareRoughness * (0.75 + 1.0), 0.05, 1.0);
    float a2 = pow(gr, 4.0);
    float k = pow(gr + 1.0, 2.0) / 8.0;
    float exposure = min(skyExposure, LocalSkyExposure);
    vec3 sum = vec3(0.0);
    vec3 l = safeNormalize(worldToView() * CelestialDirection);
    vec3 h = safeNormalize(v + l);
    float nl = max(dot(n, l), 0.0);
    float nh = max(dot(n, h), 0.0);
    float vh = max(dot(v, h), 0.0);
    float d = a2 / max(3.14159265 * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0), 1e-6);
    float g = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
    vec3 f = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    sum += f * d * g / max(4.0 * nv, 0.001) * mix(0.35, 1.0, 1.0 - gr)
            * CelestialColor * exposure * exposure * CelestialVisibility * SunOcclusion;
    vec3 wp = fragWorldPos();
    vec3 localSum = LocalLightColor1 * LocalLightStrength1 * max(dot(n, worldToView() * safeNormalize(LocalLightPos1 - wp)), 0.0)
            + LocalLightColor2 * LocalLightStrength2 * max(dot(n, worldToView() * safeNormalize(LocalLightPos2 - wp)), 0.0)
            + LocalLightColor3 * LocalLightStrength3 * max(dot(n, worldToView() * safeNormalize(LocalLightPos3 - wp)), 0.0)
            + LocalLightColor4 * LocalLightStrength4 * max(dot(n, worldToView() * safeNormalize(LocalLightPos4 - wp)), 0.0);
    sum += localSum * gr * 0.22;
    vec3 result = sum * weatherGain;
    vec3 tint = albedo / max(dot(albedo, vec3(0.299, 0.587, 0.114)), 0.1);
    result *= mix(vec3(1.0), clamp(tint, vec3(0.0), vec3(3.0)), 0.6);
    return result / (1.0 + result * 0.35);
}

// ===== 动态迷彩拼接区(枪包数据驱动)=====
// CamoShaderBuilder 会把下面两个标记之间的内容整段替换成:
//   每条枪包迷彩的片段(#define 重命名) + taczfixesCamoDispatch()
//   片段里用到的工具函数由枪包自己提供, 本模组不再内置。
// 没有任何迷彩时保留这个空实现: 原版 gun_pbr 程序照样能编译, dispatch 永远返回 0。
//<<<TACZFIXES_CAMO_BEGIN>>>
vec4 taczfixesCamoDispatch(vec2 uv, float t) { return vec4(0.0); }
//<<<TACZFIXES_CAMO_END>>>

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    float alpha = base.a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.1) discard;
    // 动态迷彩: 程序化颜色替换枪身色相, 明暗结构仍取原贴图亮度(蚀刻/凹槽/棱线保留);
    // 返回的 alpha 是混合权重, 定义里的 strength 作为上限。CamoEmissive>0 时亮带进 glow。
    vec3 camoGlow = vec3(0.0);
    if (CamoIndex > 0) {
        float camoLum = clamp(dot(base.rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        // 贴图明暗同时推动迷彩相位: 凹槽/棱线处的图案被挤到一边, 像刻在金属上而不是盖在表面
        vec2 camoUV = texCoord0 + (camoLum - 0.5) * 0.030 * vec2(1.0, 0.55);
        vec4 camoCol = taczfixesCamoDispatch(camoUV, Time);
        float camoW = clamp(camoCol.a * CamoStrength, 0.0, 1.0);
        if (camoW > 0.002) {
            // 与参考实现同款明暗模板: 暗部保底 0.45、亮部可到 1.35
            float camoShape = 0.45 + 0.90 * pow(camoLum, 0.86);
            base.rgb = mix(base.rgb, camoCol.rgb * camoShape, camoW);
            // 只有"亮带"才发光(亮带扫到哪亮到哪)
            float camoEmi = smoothstep(0.42, 1.05, max(camoCol.r, max(camoCol.g, camoCol.b)));
            camoGlow = camoCol.rgb * camoW * CamoEmissive * camoEmi;
        }
    }
    vec4 specular = HasSpecular != 0 ? texture(Sampler3, texCoord0) : vec4(0.0, 0.0, 0.0, 1.0);
    float emission = max(float(illuminatedGroup),
            specular.a < 254.5 / 255.0 ? specular.a * (255.0 / 254.0) : 0.0);
    vec3 glow = base.rgb * rawVertexColor.rgb * ColorModulator.rgb * emission * EmissionStrength;
    glow += camoGlow * rawVertexColor.rgb * ColorModulator.rgb;
    float fog = 1.0 - smoothstep(FogStart, max(FogEnd, FogStart + 0.001), vertexDistance);

    vec3 n = safeNormalize(viewNormal);
    float ao = 1.0;
    if (HasNormal != 0) {
        vec3 normalMap = texture(Sampler4, texCoord0).rgb;
        n = mappedNormal(n, normalMap.rg * 2.0 - 1.0);
        ao = normalMap.b;
    }
    // 湿润水膜: 在法线上叠一层细微波纹, 让雨天的反光有流动感。
    if (Wetness > 0.001 && WetDetail > 0.001) {
        vec2 p = viewPosition.xy * 7.0;
        float ripple = sin(p.x * 1.7 + Time * 2.7) * cos(p.y * 1.3 - Time * 2.1)
                + 0.5 * sin((p.x + p.y) * 2.3 - Time * 3.7);
        float ripple2 = cos(p.y * 1.9 + Time * 3.1) * sin(p.x * 1.1 - Time * 1.9);
        vec3 bump = vec3(ripple, ripple2, 0.0) * (0.05 * Wetness * WetDetail);
        n = safeNormalize(n + bump);
    }
    vec3 v = safeNormalize(-viewPosition);
    vec3 t;
    vec3 b;
    tangentBasis(n, t, b);

    float wet = clamp(Wetness, 0.0, 1.0);
    vec3 wetBase = base.rgb * mix(1.0, 0.7, wet);
    vec3 linearBase = pow(wetBase, vec3(2.2));
    float baseLuma = clamp(dot(linearBase, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);

    // 材质: 有 _s 图时按 LabPBR 读取, 没有时按底色亮度推导金属度(FallbackMaterial)。
    bool hasSpecular = HasSpecular != 0;
    float roughness;
    float metallic;
    vec3 f0;
    if (hasSpecular) {
        roughness = max(pow(1.0 - specular.r, 2.0), 0.045);
        metallic = step(229.5 / 255.0, specular.g);
        f0 = metallic > 0.5 ? metalF0(floor(specular.g * 255.0 + 0.5), linearBase)
                : vec3(min(specular.g, 0.16));
    } else if (FallbackMaterial != 0) {
        roughness = max(DefaultRoughness, 0.045);
        metallic = clamp(DefaultMetal * smoothstep(0.05, 0.55, baseLuma), 0.0, 1.0);
        f0 = mix(vec3(0.04), vec3(0.56, 0.57, 0.58), metallic);
    } else {
        roughness = 0.6;
        metallic = 0.0;
        f0 = vec3(0.04);
    }
    roughness = mix(roughness, max(roughness * 0.25, 0.05), wet);
    float aniso = clamp(Anisotropy, 0.0, 1.0) * clamp(metallic, 0.0, 1.0);
    // 配置里的 1.0 对应旧版 0.5 的实际强度, 这里统一折半。
    float reflectionGain = hasSpecular ? ReflectionStrength * 0.5
            : (FallbackMaterial != 0 ? max(DefaultReflection, ReflectionStrength * 0.25) : 0.0);
    float exposure = min(skyExposure, LocalSkyExposure);
    float sunVisibility = CelestialVisibility * SunOcclusion;
    // 局部光方向按像素位置重算: 火把是近处点光源, 用相机位置算方向会有明显视差,
    // 会把镜像高光点甩到尾/枪外。
    vec3 wp = fragWorldPos();
    vec3 Lw1 = safeNormalize(LocalLightPos1 - wp);
    vec3 Lw2 = safeNormalize(LocalLightPos2 - wp);
    vec3 Lw3 = safeNormalize(LocalLightPos3 - wp);
    vec3 Lw4 = safeNormalize(LocalLightPos4 - wp);

    if (EmissionPass != 0) {
        vec3 sunView = safeNormalize(worldToView() * CelestialDirection);
        float sunBehindView = smoothstep(0.05, 0.75, sunView.z);
        float backDim = mix(1.0, 0.2, sunBehindView);
        vec3 glare = glareHighlight(n, v, roughness, f0, linearBase) * (GlareStrength * 0.25) * backDim;
        vec3 total = (glow + glare) * fog;
        if (ScopeDivisionPass != 0) {
            if (emission <= 0.0 && dot(total, total) < 1.0e-6) discard;
            fragColor = vec4(total, 0.0);
            return;
        }
        float sceneDepth = texture(Sampler5, gl_FragCoord.xy / vec2(textureSize(Sampler5, 0))).r;
        if (GlareDepthTest != 0 && gl_FragCoord.z > sceneDepth + 0.000001) discard;
        fragColor = vec4(total, alpha);
        return;
    }

    // 太阳/月亮的直射高光(各向异性 GGX + 宽反光叶)。
    vec3 l = safeNormalize(worldToView() * CelestialDirection);
    vec3 celestialDirect = specGGX(n, v, t, b, l, CelestialColor,
            exposure * exposure * sunVisibility, f0, roughness, aniso, 0.0);
    float areaT = 0.35;
    vec3 hCelestial = safeNormalize(v + l);
    float nl = max(dot(n, l), 0.0);
    float nh = max(dot(n, hCelestial), 0.0);
    vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - max(dot(v, hCelestial), 0.0), 5.0);
    // 大范围高光叶: 侧对太阳/月亮时可见面上仍要能看到明确的高光斑, 而不是只有柔和环境反光。
    float nlWide = smoothstep(-0.30, 0.22, nl);
    celestialDirect += fresnel * nlWide * pow(nh, mix(32.0, 5.0, areaT))
            * CelestialColor * exposure * exposure * sunVisibility * (1.15 * areaT);

    // 多光源局部高光(火把/灯笼/枪口闪光/镭射等, 最多 4 条)。
    // 局部光用较宽的叶 + wrap, 否则低多边形枪械的大平面在光源侧对时完全看不到反光。
    vec3 localDirect = vec3(0.0);
    vec3 localWide = vec3(0.0);

    localDirect += specGGX(n, v, t, b, safeNormalize(worldToView() * Lw1),
            LocalLightColor1, LocalLightStrength1 * 0.30, f0, mix(roughness, 0.22, 0.55), aniso, 0.0);
    localDirect += specGGX(n, v, t, b, safeNormalize(worldToView() * Lw2),
            LocalLightColor2, LocalLightStrength2 * 0.30, f0, mix(roughness, 0.22, 0.55), aniso, 0.0);
    localDirect += specGGX(n, v, t, b, safeNormalize(worldToView() * Lw3),
            LocalLightColor3, LocalLightStrength3 * 0.30, f0, mix(roughness, 0.22, 0.55), aniso, 0.0);
    localDirect += specGGX(n, v, t, b, safeNormalize(worldToView() * Lw4),
            LocalLightColor4, LocalLightStrength4 * 0.30, f0, mix(roughness, 0.22, 0.55), aniso, 0.0);
    localWide += LocalLightColor1 * LocalLightStrength1 * 0.10
            * pow(max(dot(n, safeNormalize(v + safeNormalize(worldToView() * Lw1))), 0.0), mix(48.0, 4.0, areaT));
    localWide += LocalLightColor2 * LocalLightStrength2 * 0.10
            * pow(max(dot(n, safeNormalize(v + safeNormalize(worldToView() * Lw2))), 0.0), mix(48.0, 4.0, areaT));
    localWide += LocalLightColor3 * LocalLightStrength3 * 0.10
            * pow(max(dot(n, safeNormalize(v + safeNormalize(worldToView() * Lw3))), 0.0), mix(48.0, 4.0, areaT));
    localWide += LocalLightColor4 * LocalLightStrength4 * 0.10
            * pow(max(dot(n, safeNormalize(v + safeNormalize(worldToView() * Lw4))), 0.0), mix(48.0, 4.0, areaT));
    localWide *= areaT;

    // 反光基础参数: gloss/chrome 是"美术观感"的金属度, fres 控制掠射增强。
    float ndv = clamp(dot(n, v), 0.001, 1.0);
    float gloss = clamp(1.0 - roughness * 0.9, 0.0, 1.0);
    float baseRefl = min(1.0, gloss * 2.0);
    float chrome = clamp(gloss * 2.0 - 1.0, 0.0, 1.0) * clamp(metallic, 0.0, 1.0);
    float fres = mix(0.035 + 0.965 * pow(1.0 - ndv, 4.0),
            0.46 + 0.54 * pow(1.0 - ndv, 2.2), chrome);
    float kRefl = (baseRefl + 0.85 * chrome) * EnvStrength * reflectionGain;
    // 高金属度整体收一点; 非金属(木头/塑料)大幅压掉光源反光, 避免被照亮发白。
    float metalAtten = mix(1.0, 0.62, clamp(metallic, 0.0, 1.0));
    float dielectricAtten = mix(0.30, 1.0, clamp(metallic, 0.0, 1.0));

    // 环境反射: 地平线用雾色, 天顶用真实天空色, 向下取地面回弹。
    vec3 reflectedWorld = transpose(worldToView()) * reflect(-v, n);
    float s1 = LocalLightStrength1;
    float s2 = LocalLightStrength2;
    float s3 = LocalLightStrength3;
    float s4 = LocalLightStrength4;
    vec3 lightTint = LocalLightColor1 * s1 + LocalLightColor2 * s2
            + LocalLightColor3 * s3 + LocalLightColor4 * s4;
    float torchSum = clamp(s1 + s2 + s3 + s4, 0.0, 1.5);
    vec3 torchCol = torchSum > 0.01 ? lightTint / max(torchSum, 1e-3) : vec3(1.0, 0.55, 0.22);
    vec3 environment = skyEnvironment(reflectedWorld);
    // 局部光"光探针": 近火把时环境反射整体抬亮偏暖。镜像高光点滑出模型轮廓后,
    // 表面仍应有一层宽反光(DGS 的 envL = max(skyL, 0.55 * torch) 做法)。
    float envLum = dot(environment, vec3(0.2126, 0.7152, 0.0722));
    vec3 envHue = clamp(environment / max(envLum, 1e-3), vec3(0.0), vec3(1.6));
    float liftedLum = max(envLum, 0.25 * torchSum);
    environment = mix(environment, liftedLum * mix(envHue, torchCol, 0.55), 0.40);
    environment += localAmbientSum() * 0.03;
    vec3 envTint = mix(min(linearBase * 1.3, vec3(0.8)), min(f0 * 2.0, vec3(0.9)), metallic)
            * (0.15 + 0.85 * baseLuma);
    float litFacing = max(nl * sunVisibility,
            dot(n, safeNormalize(worldToView() * Lw1)) * LocalLightStrength1);
    float envFace = mix(0.55, 1.0, smoothstep(0.0, 0.4, litFacing));
    vec3 indirect = environment * envTint * fres * kRefl * mix(0.55, 1.0, envFace) * ao * 0.70;
    // 屏幕空间反射(深度步进 + 屏幕探针兜底)。
    vec4 ssr = screenSpaceReflection(n, envTint);
    float ssrWeight = clamp(SSRStrength * ssr.a * (0.45 + 0.55 * (1.0 - roughness)) * clamp(metallic, 0.0, 1.0), 0.0, 0.55);
    if (ssrWeight > 0.001) {
        indirect = mix(indirect, ssr.rgb * fres * kRefl * ao * 0.85, ssrWeight);
    }

    // 光源镜像盘 + 柔光环: 反射方向对准光源时出现点状反光(太阳/月亮/火把/枪口闪光)。
    float spread = radians(4.0 + roughness * 32.0) * (1.0 - 0.40 * chrome * (1.0 - roughness));
    float haloExp = mix(60.0, 11.0, clamp(baseRefl, 0.0, 1.0));
    float specAmt = baseRefl + 2.2 * chrome * chrome;
    vec3 Lc = safeNormalize(CelestialDirection);
    // 日/月是大面积光源, 用更宽的盘和更低的环指数; 侧对时也能看到反光。
    float spreadC = radians(6.0 + roughness * 40.0) * (1.0 - 0.35 * chrome * (1.0 - roughness));
    float haloExpC = mix(40.0, 8.0, clamp(baseRefl, 0.0, 1.0));
    float diskC = smoothstep(cos(spreadC), cos(spreadC * 0.40), dot(reflectedWorld, Lc)) * sunVisibility;
    float haloC = pow(max(dot(reflectedWorld, Lc), 0.0), haloExpC) * sunVisibility;
    float diskL = smoothstep(cos(spread), cos(spread * 0.35), dot(reflectedWorld, Lw1)) * s1
            + smoothstep(cos(spread), cos(spread * 0.35), dot(reflectedWorld, Lw2)) * s2
            + smoothstep(cos(spread), cos(spread * 0.35), dot(reflectedWorld, Lw3)) * s3
            + smoothstep(cos(spread), cos(spread * 0.35), dot(reflectedWorld, Lw4)) * s4;
    float haloL = pow(max(dot(reflectedWorld, Lw1), 0.0), haloExp * 1.15) * s1
            + pow(max(dot(reflectedWorld, Lw2), 0.0), haloExp * 1.15) * s2
            + pow(max(dot(reflectedWorld, Lw3), 0.0), haloExp * 1.15) * s3
            + pow(max(dot(reflectedWorld, Lw4), 0.0), haloExp * 1.15) * s4;
    vec3 diskTerm = (CelestialColor * diskC * 1.30 + lightTint * diskL * 0.30)
            * specAmt * kRefl * (0.5 + 0.5 * fres);
    vec3 haloTerm = (CelestialColor * haloC + lightTint * haloL * 0.45)
            * (0.06 + 0.11 * baseRefl) * specAmt * kRefl * (0.4 + 0.6 * fres) * dielectricAtten;
    // 近处火把的暖色缎光(宽反光, 与点状镜像高光互补)。
    vec3 sheenTerm = torchCol * (0.02 + 0.10 * fres) * kRefl * torchSum * dielectricAtten;
    // 日/月的广域反光: 让侧对太阳/月亮时枪身也有可见的宽反光。
    vec3 celestialSheen = CelestialColor * sunVisibility * exposure * exposure
            * (0.03 + 0.10 * fres) * kRefl * 0.45;

    vec3 reflection = (celestialDirect * ao * reflectionGain
            + localDirect * ao + localWide * ao
            + indirect + diskTerm + haloTerm + sheenTerm + celestialSheen) * metalAtten;
    float sunBehind = smoothstep(0.05, 0.75, l.z);
    reflection *= mix(1.0, 0.55, sunBehind);
    // 主通道保留一份清晰眩光核心; 泛光链路只负责外圈光晕, 避免眩光本体被模糊。
    vec3 glareCore = glareHighlight(n, v, roughness, f0, linearBase) * (GlareStrength * 0.25)
            * mix(1.0, 0.2, sunBehind) * 0.35;
    if (GlareDepthTest != 0) {
        float glareSceneDepth = texture(Sampler5, gl_FragCoord.xy / vec2(textureSize(Sampler5, 0))).r;
        glareCore *= step(gl_FragCoord.z, glareSceneDepth + 0.000001);
    }
    reflection += glareCore;
    // 高光轻微外扩(类似过曝), 提升"闪"的读感。
    float reflLum = dot(reflection, vec3(0.2126, 0.7152, 0.0722));
    float over = smoothstep(0.55, 1.30, reflLum) * clamp(baseRefl * 0.45 + chrome * 0.85, 0.0, 1.0);
    reflection += (reflection * 0.45 + envTint * 0.06) * over;
    reflection = reflection / (1.0 + reflection);

    vec3 color = wetBase * vertexColor.rgb * ColorModulator.rgb * lightMapColor.rgb;
    color += reflection * (1.0 - color);
    float sunDiffuse = max(nl, 0.0) * exposure * sunVisibility;
    color += CelestialColor * sunDiffuse * wetBase * 0.30 * ao;
    color += CelestialColor * wetBase * SkyAmbient * exposure * 0.12 * ao;
    color += localAmbientSum() * wetBase * 0.02 * ao;
    color = mix(overlayColor.rgb, color, overlayColor.a);
    color = mix(color, base.rgb * rawVertexColor.rgb * ColorModulator.rgb, clamp(emission * EmissionStrength, 0.0, 1.0));
    color += glow * 0.25;
    // 过曝守卫: 高光接近死白时按 knee/ceil 压缩, 防止整个面糊成纯白。
    if (emission <= 0.01 && reflectionGain > 0.001) {
        float hiMax = max(color.r, max(color.g, color.b));
        const float GUARD_KNEE = 0.90;
        const float GUARD_CEIL = 1.25;
        if (hiMax > GUARD_KNEE) {
            float gx = (hiMax - GUARD_KNEE) / (GUARD_CEIL - GUARD_KNEE);
            float lim = GUARD_KNEE + (GUARD_CEIL - GUARD_KNEE) * (gx / (1.0 + gx));
            color *= lim / hiMax;
        }
    }
    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
