#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // Occlusion is resolved once at bloom resolution instead of on every blur tap,
    // so the separable blur passes only fetch color.
    float emissionDepth = texture(Sampler1, texCoord).r;
    float sceneDepth = texture(Sampler2, texCoord).r;
    vec3 color = texture(Sampler0, texCoord).rgb;
    fragColor = vec4(emissionDepth > sceneDepth + 0.000001 ? vec3(0.0) : color, 1.0);
}
