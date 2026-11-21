#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
// AR 加速绘制时, 用 TaCZ 对 *_illuminated 骨骼强制写入的 FULL_BRIGHT 光照来识别发光组
// (加速缓存的网格路径上顶点标记位不可靠, 但光照值本身是每帧写入的)。
uniform int ArEmissive;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
out float vertexDistance;
out vec4 vertexColor;
out vec4 rawVertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec3 viewPosition;
out vec3 viewNormal;
out float skyExposure;
flat out int illuminatedGroup;

void main() {
    vec4 position = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * position;
    viewPosition = position.xyz;
    viewNormal = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
    skyExposure = clamp(float(UV2.y) / 240.0, 0.0, 1.0);
    vertexDistance = fog_distance(Position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    rawVertexColor = Color;
    illuminatedGroup = (((UV2.x & 1) != 0) || ((UV2.y & 1) != 0)
            || (ArEmissive != 0 && UV2.x == 240 && UV2.y == 240)) ? 1 : 0;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
