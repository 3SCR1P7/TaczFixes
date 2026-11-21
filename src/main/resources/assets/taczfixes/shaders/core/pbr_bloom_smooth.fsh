#version 150
// 泛光时间平滑: 把最新链结果按 Alpha 混合进常驻纹理, 间隔更新时不产生隔帧闪烁。
uniform sampler2D Sampler0;
uniform float Alpha;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    fragColor = vec4(texture(Sampler0, texCoord).rgb, Alpha);
}