#version 150
uniform sampler2D Sampler0;
uniform float MaskStart;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // 距屏幕边缘的距离(0 中心, 1 边缘)。用平滑 p-范数近似方形遮罩, 避免 max(|x|,|y|) 在对角线上的梯度突变
    vec2 d = abs(texCoord - 0.5) * 2.0;
    float edge = pow(pow(d.x, 6.0) + pow(d.y, 6.0), 1.0 / 6.0);
    // MaskStart 处开始出现该层模糊, 到屏幕边缘完全生效
    float mask = smoothstep(MaskStart, 1.0, edge);
    fragColor = vec4(texture(Sampler0, texCoord).rgb, mask);
}
