#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat3 IViewRotMat;
uniform int HasNormal;
uniform int HasSpecular;
uniform int EmissionPass;
uniform float ReflectionStrength;
uniform float EmissionStrength;
uniform vec3 CelestialDirection;
uniform vec3 CelestialColor;
uniform float SkyAmbient;
uniform float CelestialVisibility;
uniform float LocalSkyExposure;
in float vertexDistance;
in vec4 vertexColor;
in vec4 rawVertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 viewPosition;
in vec3 viewNormal;
in float skyExposure;
flat in int illuminatedGroup;
out vec4 fragColor;

vec3 safeNormalize(vec3 v) { return v * inversesqrt(max(dot(v, v), 1e-8)); }

// Derivative tangent frame works with mirrored Bedrock UVs; no extra vertex attributes needed.
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
    return albedo; // LabPBR custom metals use base color
}

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    float alpha = base.a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.1) discard;
    vec4 specular = HasSpecular != 0 ? texture(Sampler3, texCoord0) : vec4(0.0, 0.0, 0.0, 1.0);
    // LabPBR alpha 255 is the non-emissive sentinel, NOT maximum emission.
    float emission = max(float(illuminatedGroup),
            specular.a < 254.5 / 255.0 ? specular.a * (255.0 / 254.0) : 0.0);
    vec3 glow = base.rgb * rawVertexColor.rgb * ColorModulator.rgb * emission * EmissionStrength;
    float fog = 1.0 - smoothstep(FogStart, max(FogEnd, FogStart + 0.001), vertexDistance);
    if (EmissionPass != 0) {
        if (emission <= 0.0) discard;
        // Emission target may be smaller than the main target; sample scene depth by screen UV.
        float sceneDepth = texture(Sampler5, gl_FragCoord.xy / vec2(textureSize(Sampler5, 0))).r;
        if (gl_FragCoord.z > sceneDepth + 0.000001) discard;
        fragColor = vec4(glow * fog, alpha);
        return;
    }
    vec3 n = safeNormalize(viewNormal);
    float ao = 1.0;
    if (HasNormal != 0) {
        vec3 normalMap = texture(Sampler4, texCoord0).rgb;
        n = mappedNormal(n, normalMap.rg * 2.0 - 1.0);
        ao = normalMap.b;
    }
    vec3 v = safeNormalize(-viewPosition);
    // IViewRotMat is inverse camera rotation, independent of gun/hand model transforms.
    vec3 l = safeNormalize(transpose(IViewRotMat) * CelestialDirection);
    vec3 h = safeNormalize(v + l);
    float nv = max(dot(n, v), 0.001);
    float nl = max(dot(n, l), 0.0);
    float nh = max(dot(n, h), 0.0);
    float vh = max(dot(v, h), 0.0);
    float roughness = max(pow(1.0 - specular.r, 2.0), 0.045);
    float metallic = step(229.5 / 255.0, specular.g);
    vec3 linearBase = pow(base.rgb, vec3(2.2));
    // 介电质 F0 上限 0.16: 防止 _s 图的 reflectance 通道过高导致黑/灰枪出现大片白光
    vec3 f0 = metallic > 0.5 ? metalF0(floor(specular.g * 255.0 + 0.5), linearBase)
            : vec3(min(specular.g, 0.16));
    vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
    float a2 = pow(roughness, 4.0);
    float d = a2 / max(3.14159265 * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0), 1e-6);
    float k = pow(roughness + 1.0, 2.0) / 8.0;
    float g = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
    float exposure = min(skyExposure, LocalSkyExposure);
    // 粗糙表面压低太阳直射高光, 避免整枪发白的宽高光
    vec3 direct = fresnel * d * g / max(4.0 * nv, 0.001) * mix(0.35, 1.0, 1.0 - roughness)
            * CelestialColor * exposure * exposure * CelestialVisibility;
    vec3 reflected = IViewRotMat * reflect(-v, n);
    // 环境反射: 天空/地面渐变 + 地平线亮带, 让侧向/掠射角也有明显反光。
    // 反射必须带材质色(深色枪身反射深色环境), 否则抬头看天时天空反射会把整枪洗成灰色;
    // 金属按 F0 着色, 并带菲涅尔掠射增强。
    float sky = smoothstep(-0.35, 0.9, reflected.y);
    // 天空色降低饱和度, 避免中性色枪身反射出蓝调
    vec3 environment = mix(vec3(0.15, 0.14, 0.13), vec3(0.58, 0.63, 0.74), sky);
    float horizon = exp(-abs(reflected.y) * 5.0);
    environment += CelestialColor * horizon * 0.2 * CelestialVisibility;
    vec3 envFresnel = f0 + (1.0 - f0) * pow(1.0 - nv, 5.0);
    // 反射整体按底色亮度缩放(含金属): 黑/灰枪即使被 _s 图标成金属也不会被天空反射洗白/偏蓝
    float baseLuma = clamp(dot(linearBase, vec3(0.299, 0.587, 0.114)), 0.0, 1.0);
    vec3 envTint = mix(min(linearBase * 1.3, vec3(0.8)), min(f0 * 2.0, vec3(0.9)), metallic)
            * (0.15 + 0.85 * baseLuma);
    float envVisibility = (0.25 + 0.75 * SkyAmbient) * (0.30 + 0.70 * exposure)
            * (1.0 - roughness * 0.6);
    vec3 reflection = (direct + environment * envTint * envFresnel * envVisibility)
            * ao * ReflectionStrength;
    reflection = reflection / (1.0 + reflection);
    vec3 color = base.rgb * vertexColor.rgb * ColorModulator.rgb * lightMapColor.rgb;
    // Keep vanilla's lit albedo as the baseline. This renderer has no captured
    // environment to replace the diffuse energy removed by a full metal BRDF.
    // Darkening all metal here made it black indoors or away from the highlight.
    color += reflection * (1.0 - color);
    color = mix(overlayColor.rgb, color, overlayColor.a);
    color = mix(color, base.rgb * rawVertexColor.rgb * ColorModulator.rgb, clamp(emission * EmissionStrength, 0.0, 1.0));
    color += glow * 0.25;
    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
