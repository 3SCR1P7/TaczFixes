// 两个约定:
//   1. 所有枪包的迷彩片段会被拼进同一个 gun_pbr 程序, 片段里的顶层函数名因此必须全局唯一,
//      请一律带上自己的前缀(本示例用 dgs)。重名会让整个动态迷彩程序编译失败并回退到原版。
//   2. 同一个着色器文件可以被多条声明复用, 模组按片段内容去重、只编译一份, 每条声明通过
//      params 传自己的参数。

vec3 dgsHsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

vec2 dgsHash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453123);
}

float dgsHash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float dgsVnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = dgsHash12(i);
    float b = dgsHash12(i + vec2(1.0, 0.0));
    float c = dgsHash12(i + vec2(0.0, 1.0));
    float d = dgsHash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float dgsFbm(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; ++i) {
        s += a * dgsVnoise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return s;
}

float dgsHash11(float x) {
    return fract(sin(x * 127.1) * 43758.5453123);
}

vec3 dgsStars(vec2 uv, float scale, float t, float thresh) {
    vec2 p = uv * scale;
    float cellPx = 1.0 / max(max(fwidth(p.x), fwidth(p.y)), 1e-5);
    vec2 g = floor(p);
    vec2 f = fract(p);
    float exist = dgsHash11(dot(g, vec2(3.71, 9.17)) + 31.7);
    if (exist < thresh) {
        return vec3(0.0);
    }
    vec2 h = dgsHash22(g);
    vec2 pos = vec2(0.16, 0.16) + 0.68 * vec2(h.x, h.y);
    vec2 d = f - pos;
    float rr = max(0.030 + 0.012 * fract(h.x * 4.7), 1.37 / max(cellPx, 1.0));
    float core = exp(-dot(d, d) / (rr * rr * 0.62));
    float mag = smoothstep(thresh, 1.0, exist);
    float tw = 0.80 + 0.20 * sin(t * (0.9 + 2.4 * fract(h.y * 5.3)) + 6.2831853 * h.x);
    vec2 cs = dgsHash22(g + 77.7);
    vec3 col = mix(vec3(0.72, 0.83, 1.00), vec3(1.00, 0.97, 0.88), smoothstep(0.25, 0.75, cs.x));
    col = mix(col, vec3(1.00, 0.68, 0.52), smoothstep(0.86, 1.00, cs.y));
    float crowd = smoothstep(2.5, 6.0, cellPx);
    return core * (0.35 + 0.65 * tw) * (0.72 + 0.28 * mag) * 2.35 * col * crowd;
}

vec3 dgsStarfield(vec2 uv, float t, vec2 vel) {
    vec2 d1 = vel * t * 1.00;
    vec2 d2 = vel * t * vec2(0.74, 0.86) + vec2(0.0, vel.x * t * 0.10);
    vec2 d3 = vel * t * vec2(0.52, 0.60) - vec2(vel.y * t * 0.12, 0.0);
    vec2 d4 = vel * t * vec2(0.36, 0.44);
    return dgsStars(uv - d1, 52.0, t, 0.52)
         + dgsStars((uv - d2) * 1.85 + 4.3, 52.0, t * 1.25 + 7.0, 0.50) * 0.80
         + dgsStars((uv - d3) * 3.27 - 9.1, 52.0, t * 1.6 + 2.5, 0.48) * 0.55
         + dgsStars((uv - d4) * 4.81 + 1.7, 52.0, t * 2.1 + 5.5, 0.45) * 0.36;
}

vec3 dgsStarfield(vec2 uv, float t) {
    return dgsStarfield(uv, t, vec2(0.0));
}

vec2 dgsNodePos(vec2 cell) {
    vec2 h = dgsHash22(cell * 1.37 + 5.7);
    return cell + 0.5 + (h - 0.5) * 0.66;
}

float dgsSegDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float hh = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
    return length(pa - ba * hh);
}

vec3 dgsStarChart(vec2 uv, float t, float scale, vec3 lineCol, vec3 starCol) {
    vec2 p = uv * scale;
    float nodePx = 1.0 / max(max(fwidth(p.x), fwidth(p.y)), 1e-5);
    vec2 g = floor(p);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 2; ++i) {
        for (int j = 0; j < 2; ++j) {
            vec2 n = g - 1.0 + vec2(float(i), float(j));
            vec2 a = dgsNodePos(n);
            float da = length(p - a);
            float mag = dgsHash11(dot(n, vec2(7.31, 2.17)));
            float tw = 0.85 + 0.15 * sin(t * (0.8 + 1.7 * mag) + 6.2831853 * mag);
            float nr = max(0.037, 1.37 / max(nodePx, 1.0));
            acc += starCol * exp(-da * da / (nr * nr * 0.62)) * tw * (0.85 + 0.45 * mag) * 1.55;
            for (int k = 0; k < 3; ++k) {
                vec2 dir = k == 0 ? vec2(1.0, 0.0) : (k == 1 ? vec2(0.0, 1.0) : vec2(1.0, 1.0));
                float gate = dgsHash11(dot(n, vec2(3.13, 5.91)) + float(k) * 17.71);
                if (gate < 0.46) {
                    continue;
                }
                float d = dgsSegDist(p, a, dgsNodePos(n + dir));
                acc += lineCol * (exp(-d * d / 0.00028) + 0.16 * exp(-d * d / 0.0032))
                     * (0.55 + 0.45 * gate) * tw;
            }
        }
    }
    return acc;
}

vec2 dgsWarp(vec2 q, float t, float amt) {
    float w1 = dgsFbm(q * 1.25 - vec2(t * 0.170, t * 0.045));
    float w2 = dgsFbm(q * 1.25 + vec2(t * 0.105, -t * 0.130) + 5.2);
    return q + (vec2(w1, w2) - 0.5) * amt;
}

float dgsSwell(float g, float t, float depth) {
    const float TAU = 6.2831853;
    float a = 0.5 + 0.5 * sin(TAU * (g * 1.70 - t * 0.31));   // 快波：沿长轴正向前进
    float b = 0.5 + 0.5 * sin(TAU * (g * 0.53 + t * 0.17));   // 慢波：反向行进，制造错位
    return 1.0 + depth * (0.72 * a + 0.28 * b - 0.42);
}

float dgsFbm3(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; ++i) {
        s += a * dgsVnoise(p);
        p = p * 2.07 + vec2(4.3, 2.1);
        a *= 0.5;
    }
    return s / 0.875;
}

vec3 dgsDarkMatter(vec2 uv, float t) {
    const float TAU = 6.2831853;
    vec2 w = dgsWarp(uv, t, 0.30);
    float f = dgsFbm(w * 7.4 - vec2(t * 0.46, t * 0.07));
    float g = dgsFbm3(w * 19.0 + vec2(t * 0.34, -t * 0.42) + 3.1);
    float ridge = pow(1.0 - abs(f * 2.0 - 1.0), 7.0);
    float blob = pow(clamp(f * 1.45 - 0.34, 0.0, 1.0), 2.0);
    float s = uv.x * 9.0 + (w.y - 0.5) * 2.4 - t * 0.80 + (f - 0.5) * 2.6;
    float band = smoothstep(0.25, 0.98, sin(TAU * s));
    vec3 c = mix(vec3(0.014, 0.005, 0.036), vec3(0.130, 0.036, 0.280), f);
    c += vec3(0.560, 0.180, 0.940) * blob * 0.82;
    c += vec3(0.800, 0.600, 0.960) * ridge * 0.70;
    c += vec3(0.520, 0.160, 0.960) * band * 0.60;
    c += vec3(0.900, 0.820, 0.980) * pow(band, 7.0) * 0.42;
    c += vec3(0.300, 0.520, 0.960) * pow(clamp(g * 1.50 - 0.62, 0.0, 1.0), 2.2) * 0.60;
    c += dgsStarfield(uv + (w - uv) * 0.62, t, vec2(0.052, 0.012)) * 1.55;
    return c * dgsSwell(uv.x, t, 0.38);
}

vec3 dgsNebula(vec2 uv, float t) {
    const float TAU = 6.2831853;
    vec2 w = dgsWarp(uv, t, 0.26);
    float cloud = dgsFbm(w * 6.2 - vec2(t * 0.40, t * 0.09));
    float wisp = dgsFbm3(w * 18.0 + vec2(t * 0.30, -t * 0.50) + 5.5);
    vec2 d = (uv - 0.5) * vec2(1.20, 1.0);
    float r = length(d);
    float rot = t * 0.75 / (0.24 + r * 1.15);
    float s = uv.x * 11.0 + uv.y * 2.6 + rot * 1.45 + (cloud - 0.5) * 3.4 + (wisp - 0.5) * 1.0;
    float lane = smoothstep(-0.18, 0.60, -sin(TAU * s));
    float lane2 = smoothstep(0.35, 0.95, -sin(TAU * (uv.x * 27.0 + uv.y * 5.5 + rot * 0.7 - t * 1.90)));
    lane = max(lane, lane2 * 0.38);
    float gate = dgsFbm3(vec2(s * 0.70 + 21.0, uv.y * 5.0 - t * 0.25));
    lane *= 0.28 + 0.72 * smoothstep(0.14, 0.66, gate);
    float cycv = 0.5 + 0.5 * sin(TAU * (t * 0.090 + uv.x * 0.55 + (cloud - 0.5) * 0.18));
    float cyc = pow(cycv, 2.6);
    vec3 hot = mix(vec3(0.240, 0.580, 0.950), vec3(0.660, 0.260, 0.950), cyc);
    vec3 body = mix(vec3(0.125, 0.208, 0.569), vec3(0.300, 0.075, 0.800), cyc);
    vec3 deep = mix(vec3(0.051, 0.039, 0.173), vec3(0.070, 0.020, 0.200), cyc);
    vec3 c = mix(body, hot, pow(clamp(cloud * 1.45 - 0.10, 0.0, 1.0), 1.10));
    c = mix(c, deep, lane);
    float edge = 4.0 * lane * (1.0 - lane);
    c += mix(vec3(0.500, 0.570, 0.960), vec3(0.920, 0.540, 0.960), cyc) * edge * 0.40;
    c += hot * pow(clamp(wisp * 1.38 - 0.40, 0.0, 1.0), 1.9) * 0.98;
    c += vec3(0.880, 0.920, 1.000) * (1.0 - smoothstep(0.0, 0.16, r)) * 0.50;
    c += dgsStarfield(uv + vec2(rot * 0.015, 0.0), t) * (0.90 + 0.55 * (1.0 - lane));
    return c * dgsSwell(uv.x * 0.55 + uv.y * 0.45, t, 0.34);
}


float dgsPhaseW(float cyc, float center) {
    float d = abs(fract(cyc - center + 0.5) - 0.5);
    return 1.0 - smoothstep(0.030, 0.300, d);
}

vec3 dgsBlendHue(float mAB, float mBC, float mCA) {
    float msum = max(mAB + mBC + mCA, 1e-4);
    return (mAB * vec3(1.000, 0.330, 0.520)
          + mBC * vec3(0.330, 0.800, 1.000)
          + mCA * vec3(0.880, 0.970, 0.420)) / msum;
}

vec3 dgsStellar(vec2 uv, float t) {
    const float TAU = 6.2831853;
    const float TS = 1.55;
    float ts = t * TS;
    float g = uv.x * 0.82 + uv.y * 0.18;
    vec2 q = uv * vec2(1.7, 1.1);
    vec2 rip = vec2(sin(q.y * 26.0 + ts * 0.85), cos(q.x * 22.0 - ts * 0.65)) * 0.030
             + vec2(sin(q.x * 58.0 - ts * 1.60), 0.0) * 0.006;
    vec2 w = dgsWarp(q + rip, ts, 0.20);
    float n = dgsFbm(w * 3.9 - vec2(ts * 0.66, ts * 0.16));
    float m = dgsFbm3(w * 10.0 + vec2(-ts * 0.80, ts * 0.34) + 3.3);
    float fb = 0.5 + 0.5 * sin(TAU * (uv.x * 8.0 + (w.y - 0.5) * 1.8 - ts * 1.12 + (n - 0.5) * 2.4));
    float crack = pow(1.0 - abs(n * 2.0 - 1.0), 5.0);
    float cyc = fract(ts * 0.10 + g * 1.05 + uv.y * 0.26 + (n - 0.5) * 0.20 + (m - 0.5) * 0.09);
    float wA = dgsPhaseW(cyc, 0.0);
    float wB = dgsPhaseW(cyc, 1.0 / 3.0);
    float wC = dgsPhaseW(cyc, 2.0 / 3.0);
    float wsum = max(wA + wB + wC, 0.0001);
    wA /= wsum;
    wB /= wsum;
    wC /= wsum;
    float mol = clamp(n * 1.45 - 0.16 + 0.30 * fb, 0.0, 1.0);
    vec3 cA = mix(vec3(0.240, 0.045, 0.012), vec3(1.000, 0.420, 0.050), mol);
    cA = mix(cA, vec3(0.980, 0.800, 0.360), pow(mol, 2.4) * 0.70);
    cA += vec3(0.980, 0.700, 0.280) * (pow(clamp(m * 1.30 - 0.42, 0.0, 1.0), 3.0) * 0.50 + crack * 0.26);
    float neb = clamp(mix(n, m, 0.42) * 1.30 - 0.12 + 0.18 * fb, 0.0, 1.0);
    vec3 cB = mix(vec3(0.100, 0.020, 0.220), vec3(0.480, 0.100, 0.860), neb);
    cB = mix(cB, vec3(1.000, 0.380, 0.780), pow(clamp(neb * 1.25 - 0.25, 0.0, 1.0), 1.8) * 0.80);
    cB += vec3(0.920, 0.600, 0.980) * pow(clamp(m * 1.35 - 0.52, 0.0, 1.0), 3.0) * 0.42;
    float gas = clamp(m * 1.50 - 0.20 + 0.30 * fb, 0.0, 1.0);
    vec3 cC = mix(vec3(0.030, 0.160, 0.140), vec3(0.180, 0.860, 0.520), gas);
    cC = mix(cC, vec3(1.000, 0.560, 0.160), pow(clamp(1.0 - gas, 0.0, 1.0), 1.6) * 0.55);
    cC += vec3(0.600, 0.960, 0.800) * pow(clamp(n * 1.30 - 0.50, 0.0, 1.0), 3.0) * 0.40;
    vec3 c = wA * cA + wB * cB + wC * cC;
    const vec3 LUMK = vec3(0.2126, 0.7152, 0.0722);
    float mAB = min(wA, wB);
    float mBC = min(wB, wC);
    float mCA = min(wC, wA);
    float ov = clamp(2.0 * (mAB + mBC + mCA), 0.0, 1.0);
    vec3 shock = dgsBlendHue(mAB, mBC, mCA);
    float lumaC = max(dot(c, LUMK), 1e-3);
    float satC = (max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b))) / lumaC;
    float gray = 1.0 - smoothstep(0.22, 0.68, satC);
    vec3 shockL = shock * (lumaC / max(dot(shock, LUMK), 1e-3));
    c = mix(c, shockL, clamp(ov * gray, 0.0, 1.0));
    c += shock * ov * gray * 0.10;
    float on = clamp(wB + wC, 0.0, 1.0);
    vec2 cu = uv + vec2(ts * 0.018, sin(ts * 0.26) * 0.010) + (vec2(n, m) - 0.5) * 0.030;
    vec3 lineCol = mix(vec3(1.000, 0.800, 0.450), vec3(0.850, 0.550, 1.000), clamp(wB + 0.4 * wC, 0.0, 1.0));
    lineCol = mix(lineCol, vec3(0.550, 1.000, 0.850), wC);
    c += dgsStarChart(cu, ts, 24.0, lineCol, vec3(0.980, 0.960, 0.920)) * (0.80 + 0.45 * on);
    c += dgsStarfield(cu, ts) * (0.62 + 0.70 * on);
    float ripple = 1.0 + 0.055 * sin(uv.y * 58.0 + ts * 1.7);
    return c * dgsSwell(g, ts, 0.34) * ripple;
}

vec3 dgsViolet(vec2 uv, float t) {
    const float TAU = 6.2831853;
    vec2 w = dgsWarp(uv, t, 0.30);
    float n = dgsFbm(w * 7.2 - vec2(t * 0.60, t * 0.08));
    float m = dgsFbm3(w * 17.0 + vec2(-t * 0.80, t * 0.18) + 4.1);
    float veins = pow(clamp(n * 1.45 - 0.22, 0.0, 1.0), 2.0);
    float band = 0.5 + 0.5 * sin(TAU * (uv.x * 8.5 + (w.y - 0.5) * 1.4 - t * 0.72));
    vec3 c = mix(vec3(0.050, 0.016, 0.105), vec3(0.300, 0.100, 0.580), band);
    c += vec3(0.580, 0.240, 1.000) * veins * 0.90;
    c += vec3(0.800, 0.620, 1.000) * pow(clamp(m * 1.30 - 0.45, 0.0, 1.0), 2.4) * 0.60;
    float sheen = pow(max(0.0, sin(TAU * (uv.x * 5.2 - t * 1.10))), 12.0);
    c += vec3(0.620, 0.400, 1.000) * sheen * 0.55;
    return c * dgsSwell(uv.x, t, 0.36);
}

vec3 dgsRainbow(vec2 uv, float t) {
    vec2 w = dgsWarp(uv, t, 0.24);
    float n = dgsFbm(w * 6.4 - vec2(t * 0.50, t * 0.06));
    float m = dgsFbm3(w * 15.0 + vec2(t * 0.34, -t * 0.60) + 2.7);
    float hue = fract(uv.x * 3.2 + uv.y * 0.45 + (n - 0.5) * 0.62 + (m - 0.5) * 0.22 - t * 0.22);
    vec3 c = dgsHsv2rgb(vec3(hue, 0.86, 0.98));
    c *= 0.82 + 0.46 * pow(clamp(n * 1.40 - 0.30, 0.0, 1.0), 1.6);
    c += vec3(1.000, 1.000, 0.940) * pow(clamp(m * 1.5 - 0.62, 0.0, 1.0), 3.0) * 0.35;
    return c * dgsSwell(uv.x, t, 0.32);
}

vec3 dgsFrost(vec2 uv, float t) {
    const float TAU = 6.2831853;
    vec2 w = dgsWarp(uv, t * 0.8, 0.28);
    float n = dgsFbm(w * 8.0 - vec2(t * 0.42, t * 0.06));
    float m = dgsFbm3(w * 16.0 + vec2(t * 0.20, -t * 0.52) + 9.4);
    float band = 0.5 + 0.5 * sin(TAU * (uv.x * 6.5 + (w.y - 0.5) * 1.1 - t * 0.52));
    vec3 c = mix(vec3(0.070, 0.270, 0.500), vec3(0.480, 0.800, 1.000), band * 0.72 + n * 0.28);
    float crystal = pow(clamp(m * 1.40 - 0.46, 0.0, 1.0), 2.2);
    c += vec3(0.860, 0.960, 1.000) * crystal * 0.75;
    c += dgsStars(w * 0.85, 52.0, t * 0.9, 0.82) * 0.50;
    return c * dgsSwell(uv.x, t, 0.34);
}


// 片段的入口函数必须叫 taczfixesCamo, 返回 vec4(rgb = 迷彩色, a = 混合权重)。
vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2) {
    float t = time * max(p0.x, 0.05) + p0.z;
    float style = floor(p0.y + 0.5);
    if (style < 1.5) return vec4(dgsViolet(uv, t), 1.0);
    if (style < 2.5) return vec4(dgsRainbow(uv, t), 1.0);
    if (style < 3.5) return vec4(dgsFrost(uv, t), 1.0);
    if (style < 4.5) return vec4(dgsDarkMatter(uv, t), 1.0);
    if (style < 5.5) return vec4(dgsNebula(uv, t), 1.0);
    return vec4(dgsStellar(uv, t), 1.0);
}