#version 150
// 窄光晕 + 宽光晕合成到主目标(一次全屏 pass)。可见性已经在模糊前的遮罩 pass 里处理过。
uniform sampler2D Sampler0;
uniform sampler2D Wide;
uniform sampler2D ScopeMask;
uniform float Strength;
uniform float WideStrength;
uniform int HasScopeMask;
// 发光包围盒裁剪: ScissorRect = (x0, y0, x1, y1) 像素(左下原点), ScissorFade 边缘淡出宽度。
uniform vec4 ScissorRect;
uniform float ScissorFade;
uniform int HasScissor;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(Sampler0, texCoord).rgb * Strength;
    // 没开宽泛光层时连采样都省掉。
    if (WideStrength > 0.0) {
        color += texture(Wide, texCoord).rgb * WideStrength;
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