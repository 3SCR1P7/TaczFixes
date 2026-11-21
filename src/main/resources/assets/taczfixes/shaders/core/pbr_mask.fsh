#version 150
// 可见性遮罩: 用发光像素自己的深度和最终场景深度比较, 把被后画的前景(比如第一人称手臂)挡住的部分去掉。
// 深度是 24 位打包在 RGB 里, alpha 恒为 1(混合时不破坏深度数据)。
// 输出 alpha = 该像素光源的窗口深度(0 = 该处没有(可见的)发光)。模糊 pass 会把 alpha 一起按高斯
// 加权平均, 合成时再用它判断"这个光晕落在的前景是不是比光源更近"。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
in vec2 texCoord;
out vec4 fragColor;
float unpackDepth(vec4 enc) {
    return dot(enc.rgb, vec3(1.0, 1.0 / 255.0, 1.0 / 65025.0));
}
void main() {
    vec4 emission = texture(Sampler0, texCoord);
    float emissionDepth = unpackDepth(texture(Sampler1, texCoord));
    float sceneDepth = texture(Sampler2, texCoord).r;
    // 深度为 0 = 该像素的光源深度不可信(没写进来): 宁可丢掉这处泛光, 也不能让它漏过前景。
    bool visible = emissionDepth > 0.0 && emissionDepth <= sceneDepth + 0.000001;
    fragColor = vec4(visible ? emission.rgb : vec3(0.0), visible ? emissionDepth : 0.0);
}
