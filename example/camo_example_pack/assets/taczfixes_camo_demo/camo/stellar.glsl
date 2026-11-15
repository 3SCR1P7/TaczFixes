// 星座尽头 —— 移植自 COD Dynamic Camouflage 的"星座尽头"档：
// 恒星诞生（橙黄熔岩）→ 星云扩散（品红辉光）→ 恒星消亡（绿橙气团）三景循环融合，
// 交界处由"激波中间色"接管防止塌灰，上面再压一层会漂移的真实星图。
vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2) {
    const float TAU = 6.2831853;
    const float TS = 1.55;                  // 本档内部加速系数
    float t = (time * max(p0.x, 0.05) + p0.z) * TS;
    float g = uv.x * 0.82 + uv.y * 0.18;

    // 共用底纹：波纹位移 + 域扭曲（频率按"单个方块面里能装下结构"定标）
    vec2 q = uv * vec2(1.7, 1.1);
    vec2 rip = vec2(sin(q.y * 26.0 + t * 0.85), cos(q.x * 22.0 - t * 0.65)) * 0.030
             + vec2(sin(q.x * 58.0 - t * 1.60), 0.0) * 0.006;
    vec2 w = tfWarp(q + rip, t, 0.20);
    float n = tfFbm(w * 3.9 - vec2(t * 0.66, t * 0.16));            // 中尺度结构
    float m = tfFbm3(w * 10.0 + vec2(-t * 0.80, t * 0.34) + 3.3);    // 絮状细节
    // 沿长轴爬动的主带：三景共用，保证"这档在流"一眼看得见
    float fb = 0.5 + 0.5 * sin(TAU * (uv.x * 8.0 + (w.y - 0.5) * 1.8 - t * 1.12 + (n - 0.5) * 2.4));
    // 裂谷脊线：诞生期的"硬壳裂口"
    float crack = pow(1.0 - abs(n * 2.0 - 1.0), 5.0);

    // 三相位权重：位置项把整张图集摊开约一个完整循环，所以枪口/枪身各在一个相位
    float cyc = fract(t * 0.10 + g * 1.05 + uv.y * 0.26 + (n - 0.5) * 0.20 + (m - 0.5) * 0.09);
    float wA = tfPhaseW(cyc, 0.0);           // 恒星诞生
    float wB = tfPhaseW(cyc, 1.0 / 3.0);     // 星云扩散
    float wC = tfPhaseW(cyc, 2.0 / 3.0);     // 恒星消亡
    float wsum = max(wA + wB + wC, 0.0001);
    wA /= wsum;
    wB /= wsum;
    wC /= wsum;

    // A 恒星诞生：暗红硬壳裂开，裂口里是流动的橙黄熔岩
    float mol = clamp(n * 1.45 - 0.16 + 0.30 * fb, 0.0, 1.0);
    vec3 cA = mix(vec3(0.240, 0.045, 0.012), vec3(1.000, 0.420, 0.050), mol);
    cA = mix(cA, vec3(0.980, 0.800, 0.360), pow(mol, 2.4) * 0.70);
    cA += vec3(0.980, 0.700, 0.280) * (pow(clamp(m * 1.30 - 0.42, 0.0, 1.0), 3.0) * 0.50 + crack * 0.26);

    // B 星云扩散：深紫底 + 品红柔光，云气缓慢翻卷
    float neb = clamp(mix(n, m, 0.42) * 1.30 - 0.12 + 0.18 * fb, 0.0, 1.0);
    vec3 cB = mix(vec3(0.100, 0.020, 0.220), vec3(0.480, 0.100, 0.860), neb);
    cB = mix(cB, vec3(1.000, 0.380, 0.780), pow(clamp(neb * 1.25 - 0.25, 0.0, 1.0), 1.8) * 0.80);
    cB += vec3(0.920, 0.600, 0.980) * pow(clamp(m * 1.35 - 0.52, 0.0, 1.0), 3.0) * 0.42;

    // C 恒星消亡：绿与橙的太空气团，像被抛回宇宙的那片星骸云
    float gas = clamp(m * 1.50 - 0.20 + 0.30 * fb, 0.0, 1.0);
    vec3 cC = mix(vec3(0.030, 0.160, 0.140), vec3(0.180, 0.860, 0.520), gas);
    cC = mix(cC, vec3(1.000, 0.560, 0.160), pow(clamp(1.0 - gas, 0.0, 1.0), 1.6) * 0.55);
    cC += vec3(0.600, 0.960, 0.800) * pow(clamp(n * 1.30 - 0.50, 0.0, 1.0), 3.0) * 0.40;

    vec3 c = wA * cA + wB * cB + wC * cC;

    // 交界接管：补色各半混出来是死灰，改由"激波中间色"按等明度换上
    const vec3 LUMK = vec3(0.2126, 0.7152, 0.0722);
    float mAB = min(wA, wB);
    float mBC = min(wB, wC);
    float mCA = min(wC, wA);
    float ov = clamp(2.0 * (mAB + mBC + mCA), 0.0, 1.0);
    vec3 shock = tfBlendHue(mAB, mBC, mCA);
    float lumaC = max(dot(c, LUMK), 1e-3);
    float satC = (max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b))) / lumaC;
    float gray = 1.0 - smoothstep(0.22, 0.68, satC);
    vec3 shockL = shock * (lumaC / max(dot(shock, LUMK), 1e-3));
    c = mix(c, shockL, clamp(ov * gray, 0.0, 1.0));
    c += shock * ov * gray * 0.10;

    // 星图层：坐标自己也在漂，星座随三景循环一起游动
    float on = clamp(wB + wC, 0.0, 1.0);     // 诞生期星图被自身辉光盖掉
    vec2 cu = uv + vec2(t * 0.018, sin(t * 0.26) * 0.010) + (vec2(n, m) - 0.5) * 0.030;
    vec3 lineCol = mix(vec3(1.000, 0.800, 0.450), vec3(0.850, 0.550, 1.000), clamp(wB + 0.4 * wC, 0.0, 1.0));
    lineCol = mix(lineCol, vec3(0.550, 1.000, 0.850), wC);
    c += tfStarChart(cu, t, 24.0, lineCol, vec3(0.980, 0.960, 0.920)) * (0.80 + 0.45 * on);
    c += tfStarfield(cu, t) * (0.62 + 0.70 * on);

    // 明暗潮 + 细密涟漪
    float ripple = 1.0 + 0.055 * sin(uv.y * 58.0 + t * 1.7);
    return vec4(c * tfSwell(g, t, 0.34) * ripple, 1.0);
}
