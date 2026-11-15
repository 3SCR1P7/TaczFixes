// 流动星云 —— 移植自 COD Dynamic Camouflage 的"星云"档（按单面定标的噪声尺度原样保留）：
// 电光蓝/霓虹紫交替 + 斜向尘埃带 + 星系较差自转 + 会走的星点。
// 契约: vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2)
vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2) {
    const float TAU = 6.2831853;
    float t = time * max(p0.x, 0.05) + p0.z;
    vec2 w = tfWarp(uv, t, 0.26);
    float cloud = tfFbm(w * 6.2 - vec2(t * 0.40, t * 0.09));            // 中尺度云团
    float wisp = tfFbm3(w * 18.0 + vec2(t * 0.30, -t * 0.50) + 5.5);    // 细丝流光
    // 绕枪面中心的较差自转：越靠中心甩得越快，斜带被剪成旋涡
    vec2 d = (uv - 0.5) * vec2(1.20, 1.0);
    float r = length(d);
    float rot = t * 0.75 / (0.24 + r * 1.15);
    float s = uv.x * 11.0 + uv.y * 2.6 + rot * 1.45 + (cloud - 0.5) * 3.4 + (wisp - 0.5) * 1.0;
    float lane = smoothstep(-0.18, 0.60, -sin(TAU * s));
    // 次级细带：一个面里只有两种色块会显得扁平
    float lane2 = smoothstep(0.35, 0.95, -sin(TAU * (uv.x * 27.0 + uv.y * 5.5 + rot * 0.7 - t * 1.90)));
    lane = max(lane, lane2 * 0.38);
    // 尘埃带自己变淡、断开：宽而毛糙的带跨面只会被读成云的疏密
    float gate = tfFbm3(vec2(s * 0.70 + 21.0, uv.y * 5.0 - t * 0.25));
    lane *= 0.28 + 0.72 * smoothstep(0.14, 0.66, gate);
    // 蓝↔紫的行进式变色波：分界沿枪身往前推，"会变色"和"在流动"由同一机制保证
    float cycv = 0.5 + 0.5 * sin(TAU * (t * 0.090 + uv.x * 0.55 + (cloud - 0.5) * 0.18));
    float cyc = pow(cycv, 2.6);
    vec3 hot = mix(vec3(0.240, 0.580, 0.950), vec3(0.660, 0.260, 0.950), cyc);
    vec3 body = mix(vec3(0.125, 0.208, 0.569), vec3(0.300, 0.075, 0.800), cyc);
    vec3 deep = mix(vec3(0.051, 0.039, 0.173), vec3(0.070, 0.020, 0.200), cyc);
    vec3 c = mix(body, hot, pow(clamp(cloud * 1.45 - 0.10, 0.0, 1.0), 1.10));
    c = mix(c, deep, lane);
    // 尘埃带两侧的发射边缘（真实星云在尘埃带边缘最亮）
    float edge = 4.0 * lane * (1.0 - lane);
    c += mix(vec3(0.500, 0.570, 0.960), vec3(0.920, 0.540, 0.960), cyc) * edge * 0.40;
    c += hot * pow(clamp(wisp * 1.38 - 0.40, 0.0, 1.0), 1.9) * 0.98;
    // 星系核：小而烫的白蓝核心
    c += vec3(0.880, 0.920, 1.000) * (1.0 - smoothstep(0.0, 0.16, r)) * 0.50;
    // 星点跟着星系一起转（加在色之后，不会被尘埃带整片抹掉）
    c += tfStarfield(uv + vec2(rot * 0.015, 0.0), t) * (0.90 + 0.55 * (1.0 - lane));
    return vec4(c * tfSwell(uv.x * 0.55 + uv.y * 0.45, t, 0.34), 1.0);
}
