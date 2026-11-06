#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float NearPlane;
uniform float FarPlane;
uniform float MaxRadius;
uniform float Progress;

in vec2 texCoord;
out vec4 fragColor;

float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

void main() {
    vec3 sharp = texture(Sampler0, texCoord).rgb;
    if (MaxRadius <= 0.0 || Progress <= 0.0) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    float focus = linearDepth(texture(Sampler1, vec2(0.5)).r);
    focus = max(focus, 0.05);
    float depth = linearDepth(texture(Sampler1, texCoord).r);

    float band = max(0.15, focus * 0.02);
    float coc = abs(depth - focus);
    coc = clamp((coc - band) / max(focus, 1.0), 0.0, 1.0);
    coc = coc * coc * (3.0 - 2.0 * coc);
    float radius = MaxRadius * coc * Progress;
    if (radius < 0.35) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec3 color = sharp * 1.5;
    float total = 1.5;
    const int TAPS = 32;
    const float GOLDEN_ANGLE = 2.39996323;
    for (int i = 0; i < TAPS; i++) {
        float r = sqrt((float(i) + 0.5) / float(TAPS));
        float a = float(i) * GOLDEN_ANGLE;
        vec2 offset = vec2(cos(a), sin(a)) * r * radius * texel;
        color += texture(Sampler0, texCoord + offset).rgb;
        total += 1.0;
    }
    fragColor = vec4(color / total, 1.0);
}
