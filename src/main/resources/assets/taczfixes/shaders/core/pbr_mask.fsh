#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // Occlusion is resolved once at bloom resolution instead of on every blur tap,
    // so the separable blur passes only fetch color.
    vec4 emission = texture(Sampler0, texCoord);
    float emissionDepth = texture(Sampler1, texCoord).r;
    float sceneDepth = texture(Sampler2, texCoord).r;
    // Division pixels carry alpha 0 and bypass the scene depth check because
    // TaCZ renders sight divisions without writing depth.
    bool visible = emission.a <= 0.0 || emissionDepth <= sceneDepth + 0.000001;
    fragColor = vec4(visible ? emission.rgb : vec3(0.0), 1.0);
}
