#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float NearPlane;
uniform float FarPlane;
uniform float Blend;
uniform float Initialized;

in vec2 texCoord;
out vec4 fragColor;

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

float decode16(vec2 enc) {
    float v = floor(enc.r * 255.0 + 0.5) + floor(enc.g * 255.0 + 0.5) * 256.0;
    return clamp(v / 65535.0, 0.0, 1.0);
}

void main() {
    float current = linearDepth(texture(Sampler1, vec2(0.5)).r);
    current = max(current, 0.05);
    float previous = Initialized > 0.5
            ? decode16(texture(Sampler0, vec2(0.5)).rg) * FarPlane
            : current;
    float focus = mix(previous, current, clamp(Blend, 0.0, 1.0));
    float norm = clamp(focus / FarPlane, 0.0, 1.0);
    float v = floor(norm * 65535.0 + 0.5);
    fragColor = vec4(mod(v, 256.0) / 255.0, floor(v / 256.0) / 255.0, 0.0, 1.0);
}
