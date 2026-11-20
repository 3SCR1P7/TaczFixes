#version 150
// 窄光晕 + 宽光晕合成到主目标(一次全屏 pass)。可见性已经在模糊前的遮罩 pass 里处理过。
uniform sampler2D Sampler0;
uniform sampler2D Wide;
uniform sampler2D ScopeMask;
uniform float Strength;
uniform float WideStrength;
uniform int HasScopeMask;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = (texture(Sampler0, texCoord).rgb * Strength
            + texture(Wide, texCoord).rgb * WideStrength) * 1.6;
    if (HasScopeMask != 0) {
        float lens = clamp(texture(ScopeMask, texCoord).r, 0.0, 1.0);
        color *= 1.0 - lens;
    }
    fragColor = vec4(color, 0.0);
}