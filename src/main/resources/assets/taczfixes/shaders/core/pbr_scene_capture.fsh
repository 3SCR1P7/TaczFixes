#version 150
// 半分辨率场景捕获: 采样主目标的颜色与深度纹理, 写到两张半分辨率 RGBA8 目标。
// 颜色给 SSR 做探针/倒影; 深度按 16 位打包到 RG, 供 SSR 的深度步进比较。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord;
out vec4 colorOut;
out vec4 depthOut;
void main() {
    colorOut = vec4(texture(Sampler0, texCoord).rgb, 1.0);
    float d = clamp(texture(Sampler1, texCoord).r, 0.0, 1.0);
    float scaled = d * 255.0;
    float hi = floor(scaled);
    float lo = scaled - hi;
    depthOut = vec4(hi / 255.0, lo, 0.0, 1.0);
}
