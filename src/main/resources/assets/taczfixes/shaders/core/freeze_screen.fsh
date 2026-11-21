#version 150
// 常凪冻结: 用冻结瞬间的快照覆盖当前画面(画面静止), 并在边缘叠加平滑的红色径向暗角。
uniform sampler2D Sampler0;
uniform float FreezeOpacity;
uniform float RedStrength;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(Sampler0, texCoord).rgb;
    vec2 uv = texCoord * 2.0 - 1.0;
    float dist = length(uv * vec2(1.0, 0.82));
    float edge = smoothstep(0.55, 1.30, dist);
    vec3 red = vec3(0.70, 0.035, 0.025);
    color = mix(color, red, clamp(edge * RedStrength, 0.0, 1.0));
    fragColor = vec4(color, clamp(FreezeOpacity, 0.0, 1.0));
}