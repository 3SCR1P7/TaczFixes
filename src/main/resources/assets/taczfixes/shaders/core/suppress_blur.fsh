#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float MaskStart;
uniform float MaskStartWeak;
uniform float BlurOpacity;
uniform float AbOpacity;
uniform float Darken;
uniform float DarkenMaskStart;
uniform float DarkenOpacity;
uniform vec2 Aberration;
in vec2 texCoord;
out vec4 fragColor;

// 以中心为原点径向错开 R/B 通道实现色散
vec3 chromatic(sampler2D tex, vec2 uv, vec2 offset) {
    float r = texture(tex, uv + offset).r;
    float g = texture(tex, uv).g;
    float b = texture(tex, uv - offset).b;
    return vec3(r, g, b);
}

void main() {
    // 距屏幕边缘的距离(0 中心, 1 边缘)。用平滑的 p-范数近似方形遮罩:
    // max(|x|,|y|) 在四条对角线上梯度突变, 会形成可见的对角线亮带。
    vec2 d = (texCoord - 0.5) * 2.0;
    vec2 ad = abs(d);
    float edge = pow(pow(ad.x, 6.0) + pow(ad.y, 6.0), 1.0 / 6.0);
    vec2 offset = d * Aberration * AbOpacity;
    // 原始画面先加色散, 再按遮罩叠加上两层模糊(越靠屏幕中心模糊越弱)
    vec3 color = chromatic(Sampler2, texCoord, offset);
    float weak = smoothstep(MaskStartWeak, 1.0, edge) * BlurOpacity;
    float strong = smoothstep(MaskStart, 1.0, edge) * BlurOpacity;
    color = mix(color, chromatic(Sampler0, texCoord, offset), weak);
    color = mix(color, chromatic(Sampler1, texCoord, offset), strong);
    // 屏幕边缘变暗: 越靠近边缘遮罩越强, 向黑色混合
    float darken = smoothstep(DarkenMaskStart, 1.0, edge) * DarkenOpacity * Darken;
    color = mix(color, vec3(0.0), darken);
    fragColor = vec4(color, 1.0);
}
