#version 150
uniform sampler2D Sampler0;
uniform vec2 Direction;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = vec3(0.0);
    float total = 0.0;
    // 遮罩 pass 把光源深度写在 alpha 里: 只让"真的有发光"的采样点参与深度平均,
    // 空白像素不参与, 否则深度会被拉向远处, 合成时无法判断前景遮挡。
    float depthSum = 0.0;
    float depthWeight = 0.0;
    vec2 size = vec2(textureSize(Sampler0, 0));
    float sigma = max(length(Direction * size), 0.5);
    vec2 stepUV = Direction / sigma;
    int support = int(ceil(min(sigma * 2.0, 48.0)));
    for (int i = -support; i < support; i += 2) {
        float f0 = float(i);
        float f1 = f0 + 1.0;
        float w0 = exp(-0.5 * (f0 / sigma) * (f0 / sigma));
        float w1 = exp(-0.5 * (f1 / sigma) * (f1 / sigma));
        float wsum = w0 + w1;
        vec4 s = texture(Sampler0, texCoord + stepUV * (f0 + w1 / wsum));
        color += s.rgb * wsum;
        if (s.a > 0.0) {
            depthSum += s.a * wsum;
            depthWeight += wsum;
        }
        total += wsum;
    }
    float fs = float(support);
    float ws = exp(-0.5 * (fs / sigma) * (fs / sigma));
    vec4 edge = texture(Sampler0, texCoord + stepUV * fs);
    color += edge.rgb * ws;
    if (edge.a > 0.0) {
        depthSum += edge.a * ws;
        depthWeight += ws;
    }
    total += ws;
    fragColor = vec4(color / total, depthWeight > 0.0 ? depthSum / depthWeight : 0.0);
}
