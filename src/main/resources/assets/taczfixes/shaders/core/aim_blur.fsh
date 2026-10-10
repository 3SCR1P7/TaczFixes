#version 150
uniform sampler2D Sampler0;
uniform float MaskStart;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // 距屏幕边缘的距离(0 中心, 1 边缘), 取两轴较大值形成方形遮罩
    vec2 d = abs(texCoord - 0.5) * 2.0;
    float edge = max(d.x, d.y);
    // MaskStart 处开始出现该层模糊, 到屏幕边缘完全生效
    float mask = smoothstep(MaskStart, 1.0, edge);
    fragColor = vec4(texture(Sampler0, texCoord).rgb, mask);
}
