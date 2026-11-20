#version 150
// 可见性遮罩: 用发光像素自己的深度和最终场景深度比较, 把被后画的前景(比如第一人称手臂)挡住的部分去掉。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
in vec2 texCoord;
out vec4 fragColor;
float unpackDepth(vec4 enc) {
    return dot(enc, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}
void main() {
    vec4 emission = texture(Sampler0, texCoord);
    float emissionDepth = unpackDepth(texture(Sampler1, texCoord));
    float sceneDepth = texture(Sampler2, texCoord).r;
    bool visible = emissionDepth <= 0.0 || emissionDepth <= sceneDepth + 0.000001;
    fragColor = vec4(visible ? emission.rgb : vec3(0.0), 1.0);
}