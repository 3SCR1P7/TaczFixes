# grip_emx 两脚架动画（趴下展开 / 站立收起）

针对 EMX-Arms 枪包里的 `grip_emx_synchronized0` 握把配件：

- 站立（未匍匐）时保持收起姿态；
- 趴下（TaCZ 匍匐状态，`context:isCrawl()` 为 true）时展开两脚架；
- 站起来后自动收起；
- 展开/收起各有一次 0.32s 的过渡动画（带一点过冲回弹）。

## 文件

| 文件 | 放到枪包里的位置 |
| --- | --- |
| `grip_emx_synchronized_animations.animation.json` | `assets/emxarms/animations/grip_emx_synchronized_animations.animation.json` |
| `grip_emx_synchronized_state_machine.lua` | `assets/emxarms/scripts/grip_emx_synchronized_state_machine.lua` |
| `grip_emx_synchronized0_display.json` | `assets/emxarms/display/attachments/grip_emx_synchronized0_display.json`（覆盖原文件） |

如果放进你自己的命名空间，把上面路径里的 `emxarms` 和 display JSON 里的 `emxarms:` 都换成你的命名空间即可。

## display 的改动

原 `grip_emx_synchronized0_display.json` 只增加了 `taczfixes` 块（其余保持不变）：

```json
"taczfixes": {
  "animation": "emxarms:grip_emx_synchronized_animations",
  "state_machine": "emxarms:grip_emx_synchronized_state_machine",
  "state_machine_param": { "crouch_also_deploy": false }
}
```

- `model` 继续使用 `emxarms:attachment/grip_emx_synchronized0_geo`（收起状态模型，作为静止默认姿态）；
- `grip_emx_synchronized1` 的 display 也改成了同一套收起模型 + 同一动画（原本的展开模型不再使用），这样两个配件 id 装上都会在趴下时展开。如果只想保留一个配件，删掉另一个的 index/data/recipe/display 即可。

## 动画原理（重要：动画值是相对基础姿态的增量）

`grip_emx_synchronized0_geo` 与 `grip_emx_synchronized1_geo` 的骨骼/立方体完全相同，只有两条腿骨骼 `l2` / `l3` 的 X 角度和 1/32 单位的枢轴偏移不同：

| 状态 | l2 | l3 |
| --- | --- | --- |
| 收起（synchronized0，作为 display 基础模型） | `[-85, 0, -22.5]` | `[-85, 0, 22.5]` |
| 展开（synchronized1） | `[0, 0, -22.5]` | `[0, 0, 22.5]` |

TaCZ 的模型动画是**在模型基础姿态上叠加**的（`BedrockPart.translateAndRotateAndScale` 先应用基础旋转，再乘动画的 `additionalQuaternion`），所以动画 JSON 里写的不是绝对角度，而是相对收起模型的**增量**：

| 动画 | l2 / l3 的 X 增量 | 实际姿态 |
| --- | --- | --- |
| `collapsed_idle` | `0` | 保持基础模型（收起，-85°） |
| `deployed_idle` | `+85` | -85 + 85 = 0°（展开） |
| `deploy` | `0 → 89 → 85` | 展开并轻微过冲回弹 |
| `retract` | `85 → -4 → 0` | 收起并轻微过冲回弹 |

因此不需要新模型：显示模型用 `synchronized0`，动画只给 `l2` / `l3` 加 X 轴增量即可。

## 可调参数

`display` 的 `state_machine_param`：

- `crouch_also_deploy`：默认 `false`。设为 `true` 后“蹲下”（sneak）也会展开，方便没有匍匐的枪械/测试。

若想调整手感，改 `grip_emx_synchronized_animations.animation.json`：

- `deploy` / `retract` 的 `animation_length`（默认 0.32s）；
- `0.22` 处的过冲增量（默认 `+89` / `-4`，即展开时略微超过竖直、收起时略微超过收起位）；
- `collapsed_idle` / `deployed_idle` 的静止角度。

## 测试

1. 把三个文件按上表放进枪包（或直接用已打补丁的实例包）。
2. 启动游戏 / F3+T 重载资源。
3. 给武器装上 `grip_emx_synchronized0`。
4. 站立时观察脚架收起；按 TaCZ 匍匐键趴下，脚架应在 0.32s 内展开；站起后自动收起。
5. 如果趴下没有反应：确认枪械 data 的 `can_crawl` 为 `true`（TaCZ 匍匐要求），或把 `crouch_also_deploy` 设为 `true` 用蹲下测试。
