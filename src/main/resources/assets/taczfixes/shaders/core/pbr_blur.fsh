#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 Direction;
uniform int CheckDepth;
in vec2 texCoord;
out vec4 fragColor;
vec3 sampleVisible(vec2 uv) {
    if (CheckDepth != 0 && texture(Sampler1, uv).r > texture(Sampler2, uv).r + 0.000001) return vec3(0.0);
    return texture(Sampler0, uv).rgb;
}
void main() {
    vec3 color = vec3(0.0);
    float total = 0.0;
    vec2 size = vec2(textureSize(Sampler0, 0));
    float sigma = max(length(Direction * size), 0.5);
    vec2 stepUV = Direction / sigma;
    int support = int(ceil(min(sigma * 2.0, 48.0)));
    // One sample per pixel, even at the largest configured radius. Sparse taps
    // create repeated copies of thin lights and visible grids at large radii.
    for (int i = -support; i <= support; i++) {
        float distance = float(i) / sigma;
        float weight = exp(-0.5 * distance * distance);
        color += sampleVisible(texCoord + stepUV * float(i)) * weight;
        total += weight;
    }
    fragColor = vec4(color / total, 1.0);
}
