### 动态迷彩

动态迷彩完全由枪包提供：**data 声明着色器，assets 编写 GLSL 片段**。

目录结构：

```
tacz/<枪包>/
├── data/<namespace>/camo/<name>.json     # 声明：名称 / 着色器位置 / 强度 / 自发光 / 参数
└── assets/<namespace>/camo/<name>.glsl   # 着色器片段（默认位置）
```

声明示例 `data/mypack/camo/nebula.json`：

```json
{
  "name": "mypack.camo.nebula",
  "shader": "mypack:camo/nebula",
  "strength": 0.92,
  "emissive": 0.35,
  "params": [
    [2.6, 0.85, 1.6, 0.0],
    [0.10, 0.22, 0.85, 1.0],
    [0.55, 0.18, 0.95, 1.0]
  ]
}
```

| 字段 | 可选 | 说明 |
| --- | --- | --- |
| `name` | 是 | 显示名：语言键（放在枪包 `assets/<namespace>/lang/` 下）或纯文本；缺省为 `<namespace>:<name>` |
| `shader` | 是 | 片段位置，推荐写 `"<namespace>:camo/<name>"`（相对 assets 的路径，`.glsl` 可省略）；纯相对路径则相对 `assets/<namespace>/camo/`；缺省 = 与 data 同名的 `.glsl` |
| `strength` | 是 | 混色强度 0~1，默认 1 |
| `emissive` | 是 | 亮带自发光 0~1，默认 0 |
| `params` | 是 | 最多 3 个 `vec4`，作为 `p0`/`p1`/`p2` 传给片段，不足补 0 |

着色器片段（`assets/mypack/camo/nebula.glsl`）必须定义这个函数：

```glsl
vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2) {
    // uv    : 枪身贴图 UV（0~1）；同一把枪的各个面共用同一套贴图坐标
    // time  : 秒（全局时间，小时级回绕）
    // p0..p2: 上面 params 的三个 vec4
    // 返回  : rgb = 迷彩色；a = 混合权重（0 不动，1 完全替换色相；明暗结构仍取原贴图亮度，
    //         蚀刻/凹槽/棱线会保留）
    return vec4(1.0, 0.4, 0.1, 0.8);
}
```

- 片段里**不要**写 `#version`、`main`、`uniform`/`sampler`；需要外部数据用 `params`。
- 内置工具函数（可直接调用，请勿重名）：`tfSat`、`tfHash11/12/21/22`、`tfRot`、`tfNoise`、`tfFbm`、`tfFbm3`、`tfWarp`、`tfSwell`、`tfStars`、`tfStarfield`、`tfStarChart`、`tfPhaseW`、`tfBlendHue`、`tfHsv`
- 迷彩选择存在物品 NBT 里，随枪械同步。依赖本模组的 PBR 枪械渲染管线。