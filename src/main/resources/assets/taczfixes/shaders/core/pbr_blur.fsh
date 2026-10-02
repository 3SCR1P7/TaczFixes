#version 150
uniform sampler2D Sampler0;
uniform vec2 Direction;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = vec3(0.0);
    float total = 0.0;
    vec2 size = vec2(textureSize(Sampler0, 0));
    float sigma = max(length(Direction * size), 0.5);
    vec2 stepUV = Direction / sigma;
    int support = int(ceil(min(sigma * 2.0, 48.0)));
    // Pair adjacent texels into one bilinear tap: same Gaussian as one fetch per
    // texel, but half the texture fetches. stepUV is exactly one texel.
    for (int i = -support; i < support; i += 2) {
        float f0 = float(i);
        float f1 = f0 + 1.0;
        float w0 = exp(-0.5 * (f0 / sigma) * (f0 / sigma));
        float w1 = exp(-0.5 * (f1 / sigma) * (f1 / sigma));
        float wsum = w0 + w1;
        color += texture(Sampler0, texCoord + stepUV * (f0 + w1 / wsum)).rgb * wsum;
        total += wsum;
    }
    // The unpaired outermost texel of the symmetric kernel.
    float fs = float(support);
    float ws = exp(-0.5 * (fs / sigma) * (fs / sigma));
    color += texture(Sampler0, texCoord + stepUV * fs).rgb * ws;
    total += ws;
    fragColor = vec4(color / total, 1.0);
}
