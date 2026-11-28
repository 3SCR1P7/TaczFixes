#version 150
// 窄光晕 + 宽光晕合成到主目标(一次全屏 pass)。
// 遮挡: 模糊链把每个像素的光源深度留在 alpha 里, 这里和场景深度比较, 前景(手臂等)比光源近时
// 光晕就衰减掉 —— 只在模糊前按光源像素剔除是不够的, 光晕本身会从旁边漏到前景上。
uniform sampler2D Sampler0;
uniform sampler2D Wide;
uniform sampler2D ScopeMask;
uniform sampler2D SceneDepth;
uniform sampler2D NarrowDepth;
uniform sampler2D WideDepth;
uniform float Strength;
uniform float WideStrength;
uniform int HasScopeMask;
uniform int HasSeparateDepth;
// 发光包围盒裁剪: ScissorRect = (x0, y0, x1, y1) 像素(左下原点), ScissorFade 边缘淡出宽度。
uniform vec4 ScissorRect;
uniform float ScissorFade;
uniform int HasScissor;
in vec2 texCoord;
out vec4 fragColor;

// blurDepth 为模糊后的光源窗口深度(0 表示附近没有光源)。场景比光源近就判定被遮挡。
float occlusion(float sceneDepth, float blurDepth) {
    if (blurDepth <= 0.0) {
        return 1.0;
    }
    return smoothstep(-0.06, -0.01, sceneDepth - blurDepth);
}

void main() {
    float sceneDepth = texture(SceneDepth, texCoord).r;
    vec4 narrow = texture(Sampler0, texCoord);
    // Temporal smoothing keeps only color. Otherwise the blur texture already carries depth.
    float narrowDepth = HasSeparateDepth != 0 ? texture(NarrowDepth, texCoord).a : narrow.a;
    vec3 color = narrow.rgb * Strength * occlusion(sceneDepth, narrowDepth);
    // 没开宽泛光层时连采样都省掉。
    if (WideStrength > 0.0) {
        vec4 wide = texture(Wide, texCoord);
        float wideDepth = HasSeparateDepth != 0 ? texture(WideDepth, texCoord).a : wide.a;
        color += wide.rgb * WideStrength * occlusion(sceneDepth, wideDepth);
    }
    color *= 1.6;
    if (HasScopeMask != 0) {
        float lens = clamp(texture(ScopeMask, texCoord).r, 0.0, 1.0);
        color *= 1.0 - lens;
    }
    // 在裁剪矩形边缘做平滑淡出, 避免硬切出一条跳动的亮线。
    if (HasScissor != 0) {
        float edge = min(min(gl_FragCoord.x - ScissorRect.x, ScissorRect.z - gl_FragCoord.x),
                         min(gl_FragCoord.y - ScissorRect.y, ScissorRect.w - gl_FragCoord.y));
        float fade = clamp(edge / max(ScissorFade, 1.0), 0.0, 1.0);
        color *= fade * fade * (3.0 - 2.0 * fade);
    }
    // 泛光为 0 的像素直接丢弃: 加法混合下写 0 和不写结果相同, 但省掉主渲染目标的读写带宽。
    if (max(color.r, max(color.g, color.b)) <= 0.0001) {
        discard;
    }
    fragColor = vec4(color, 0.0);
}
