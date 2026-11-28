#version 150
uniform sampler2D Sampler0;
uniform vec2 Direction;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    if (all(equal(Direction, vec2(0.0)))) {
        fragColor = texture(Sampler0, texCoord);
        return;
    }
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
    // Start at -support. Each adjacent weight ratio differs by exp(-1 / sigma^2).
    float variance = sigma * sigma;
    float edge = float(support) / sigma;
    float edgeWeight = exp(-0.5 * edge * edge);
    float weight = edgeWeight;
    float ratio = exp((float(support) - 0.5) / variance);
    float ratioStep = exp(-1.0 / variance);
    for (int i = -support; i < support; i += 2) {
        float nextWeight = weight * ratio;
        float wsum = weight + nextWeight;
        vec4 s = texture(Sampler0, texCoord + stepUV * (float(i) + nextWeight / wsum));
        color += s.rgb * wsum;
        if (s.a > 0.0) {
            depthSum += s.a * wsum;
            depthWeight += wsum;
        }
        total += wsum;
        ratio *= ratioStep;
        weight = nextWeight * ratio;
        ratio *= ratioStep;
    }
    vec4 edgeSample = texture(Sampler0, texCoord + stepUV * float(support));
    color += edgeSample.rgb * edgeWeight;
    if (edgeSample.a > 0.0) {
        depthSum += edgeSample.a * edgeWeight;
        depthWeight += edgeWeight;
    }
    total += edgeWeight;
    fragColor = vec4(color / total, depthWeight > 0.0 ? depthSum / depthWeight : 0.0);
}
