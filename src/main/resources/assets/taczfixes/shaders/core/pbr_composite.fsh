#version 150
uniform sampler2D Sampler0;
uniform sampler2D ScopeMask;
uniform sampler2D EmissionDepth;
uniform sampler2D SceneDepth;
uniform float Strength;
uniform int HasScopeMask;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(Sampler0, texCoord).rgb * Strength * 1.6;
    float emissionDepth = texture(EmissionDepth, texCoord).r;
    float sceneDepth = texture(SceneDepth, texCoord).r;
    bool foreground = sceneDepth < 0.999999;
    bool visible = !foreground || (emissionDepth > 0.0 && emissionDepth <= sceneDepth + 0.000001);
    color *= visible ? 1.0 : 0.0;
    if (HasScopeMask != 0) {
        float lens = clamp(texture(ScopeMask, texCoord).r, 0.0, 1.0);
        color *= 1.0 - lens;
    }
    fragColor = vec4(color, 0.0);
}
