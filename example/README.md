# 枪包开发者功能说明

本目录是 TaCZ Fixes 的枪包开发者示例与速查。文件都可以直接复制进枪包，按需改名。
JSON 支持 `//` 注释；改完游戏内按 `F3+T` 重载资源（或重启）即可生效。

枪包目录结构（TaCZ 枪包，zip 或文件夹放在 `tacz/` 下）：

```
tacz/<枪包>/
├── gunpack.meta.json                           # TaCZ 需要；写 "namespace": "<命名空间>"
├── data/<namespace>/
│   ├── data/guns/<枪>.json                      # 枪械 data（内含 taczfixes 块）
│   ├── data/attachments/<配件>.json             # 配件 data（内含 taczfixes 块）
│   ├── data/blocks/<工作台>.json                # 枪械工作台方块 data（内含 taczfixes 块）
│   ├── recipes/<配方>.json                      # 合成配方（内含 taczfixes 块）
│   └── camo/<迷彩>.json                         # 动态迷彩声明
└── assets/<namespace>/
    ├── display/attachments/<配件>.json          # 配件 display（内含 taczfixes 块）
    ├── animations/<动画>.animation.json         # 配件动画（基岩版动画格式）
    ├── scripts/<脚本>.lua                       # 枪械逻辑机 / 配件状态机脚本
    ├── hud/<页面>.html                          # ApricityUI HTML HUD
    └── camo/<片段>.glsl                         # 动态迷彩 GLSL 片段
```

| 示例文件 | 放到枪包里的位置 | 说明 |
| --- | --- | --- |
| `example_gun_data.json` | `data/<ns>/data/guns/<枪>.json` | 把其中的 `taczfixes` 块加进自己的枪 data（其余字段是 TaCZ 原生字段的写法参考） |
| `example_attachment_data.json` | `data/<ns>/data/attachments/<配件>.json` | 加 `taczfixes` 块：自定义槽位、数值调整、开火模式 |
| `example_attachment_display.json` | `assets/<ns>/display/attachments/<配件>.json` | 加 `group_offset` / `stepless` / `aiming_blur` |
| `example_attachment_animation_display.json` | 同上 | 加 `taczfixes` 块接线配件动画（`animation` / `state_machine` / `state_machine_param`） |
| `example_attachment_animations.json` | `assets/<ns>/animations/<动画>.animation.json` | 配件动画文件 |
| `example_attachment_state_machine.lua` | `assets/<ns>/scripts/<脚本>.lua` | 配件动画 Lua 状态机 |
| `example_logic.lua` | `assets/<ns>/scripts/<脚本>.lua` | 枪械逻辑机脚本（枪 data 的 `"script": "<ns>:<脚本>"` 引用） |
| `example_custom_hud.html` | `assets/<ns>/hud/<页面>.html` | ApricityUI HTML HUD（枪 data 的 `custom_hud` 引用） |
| `example_crafting_data.json` | `data/<ns>/data/blocks/<工作台>.json` | 工作台方块 data 的 `taczfixes` 块（自动合成电量/时间） |
| `example_recipe.json` | `data/<ns>/recipes/<配方>.json` | `tacz:gun_smith_table_crafting` 配方里的 `taczfixes` 块 |
| `grip_emx_bipod/` | 见其 `README.md` | 完整可运行的配件动画示例（两脚架趴下展开） |
| `camo_example_pack/` | 整个文件夹即枪包 | 完整可运行的动态迷彩示例（详见其 `README.md`） |

---

## 1. 枪械 data（`taczfixes` 块）

放在 `data/<ns>/data/guns/<枪>.json` 顶层，与 TaCZ 字段并列。完整字段与注释见 `example_gun_data.json`。

| 字段 | 用法 |
| --- | --- |
| `custom_hud` | HTML HUD 页面 id（`<ns>:hud/<页面>.html` 或 `<ns>:<页面>.html`）。未装 ApricityUI 时无效并按原版 HUD 显示 |
| `enchantment_ability` | 附魔能力值（影响附魔台可获得附魔） |
| `allow_shooting_underwater` | 是否允许水下开火 |
| `suppression` | 压制效果（检测范围、曝光/模糊/变暗的强度、持续时间、范围） |
| `durability` | 枪械耐久：上限、每次开火消耗、耗尽行为（`remove`/`disable`/`none`）、铁砧修复材料与修复量、是否允许合并修复、是否显示耐久条 |
| `blocking` | 贴墙阻挡：启用距离区间、模型倾斜角度/后退/水平偏移、子弹偏转、过近禁止开火/瞄准 |
| `imbuement` | 法术灌注（Iron's Spells）：次数、冷却、魔力/冷却倍率 |
| `charge` | 电量（FE）：上限、每次开火消耗、替换弹药 HUD、电量不足阻止开火、用耐久条/过热条显示电量 |
| `light` | 开火/爆炸/子弹的彩色动态光：持续时间、初始/结束光照等级、颜色 |
| `aiming_stamina` | 屏息/瞄准耐力：消耗/恢复倍率、恢复延迟、最低耐力、屏息消耗、重量消耗、晃动幅度/速度等 |
| `stamina` | 开火/近战等动作的耐力消耗与恢复倍率 |
| `dual_wield` | 双持：启用、后坐力/散布倍率、准星聚焦倍率、左右手位置偏移、换弹/移动动画模式、左右手臂定位组 |
| `jump_inaccuracy` | 滞空散布倍率与涨落速度 |
| `bullet_ricochet` | 跳弹：角度区间、概率区间、可跳弹方块 tag、伤害倍率、反射角比例、是否含上下表面 |
| `fire_mode` | 自定义开火模式（id → `type` + `burst_data` + `adjust` + `icon`）；`getFireMode()` 可见 |
| `damage_by_distance` | 优势射程与距离伤害函数（Lua 表达式，`x` = 飞行距离 - 有效射程，`y` = 伤害倍率） |
| `allow_animation_zoom` | 加速拉栓/换弹时是否同步加速动画播放 |
| `bullet_in_barrel` | 换弹额外装填发数：额外装填 `min(换弹前弹药, 值)`；负数减少。仅 `MAGAZINE` 弹匣换弹逐发扣背包，`FUEL` 不额外扣 |
| `refit_point` | 改装点数 |
| `limb_factor` | 四肢伤害倍率（数字或按部位的倍率表） |
| `inaccuracy_multiplier` | 连射惩罚：按 stand/aim/lie/sneak/move 的回复延迟、回复速度、最大堆叠、每发百分比/固定增量 |
| `recoil_multiplier` | 按开火模式的后坐力修饰器：`window` 宽容时间 + `modifiers[count / count 区间 / [n,"infinite",步长], pitch_multiplier, yaw_multiplier]` |
| `fire_knockback` | 开火击退力度、潜行力度、半自动/连发倍率 |
| `attachment_slots` | 自定义配件槽位，见下一节 |

### 自定义配件槽位（`attachment_slots`）

```json
"attachment_slots": {
  "hidden_unavailable": true,          // 隐藏未启用的自定义槽，默认 true
  "hidden_unavailable_default": true,  // 隐藏未启用的原生槽，默认 false
  "laser2": {
    "type": "laser",                   // laser/grip/muzzle/stock/scope/custom
    "pos_alter": [0.5, -0.5],          // 槽位位置微调
    "dependence": { "laser": true, "scope": ["#tacz:scope"] },
    "conflict":   { "handguard": ["#tacz:handguard"] },
    "builtin_attachments": {
      "attachments": ["emx_re:am_push_1", "emx_re:am_push_2"],
      "default_attached": "emx_re:am_push_1"
    }
  },
  "handguard": {
    "type": "custom",
    "name": "tacz.slot.handguard.name", // custom 类型的本地化键
    "slot": "tacz:slot/handguard",      // custom 类型的图标
    "allow_attachments": ["#tacz:handguard", "tacz:laser_compact"],
    "blacklist": ["#tacz:blacklisted"],
    "whitelist": ["#tacz:whitelisted"]
  },
  "scope2": { "type": "scope", "angle": 45.0, "offset": 0.1 }
}
```

- 槽位 id 对应模型里的 `*_pos` / `*_default` / `*_adapter` 组，不能与 TaCZ 已有槽位 id 重复。
- `type` 不为 `custom` 时使用该类型的原生槽位名称/图标/可装配件类型。
- `dependence`：所列槽位满足条件时才启用；`true` = 该槽位装了东西即可，数组/tag = 需要指定配件。
- `conflict`：所列槽位有指定配件时禁用此槽位。
- `builtin_attachments`：改装时可直接选的原厂配件（不进背包）。
- `scope`/`grip` 槽的 `angle`、`offset` 控制开镜时枪身倾斜与视角后退。
- 多个瞄具槽装瞄具后，游戏内用切换瞄具键循环切换。

## 2. 配件 data（`taczfixes` 块）

放在 `data/<ns>/data/attachments/<配件>.json` 顶层。完整字段见 `example_attachment_data.json`。

| 字段 | 用法 |
| --- | --- |
| `attachment_slots` | 此配件自己提供的额外槽位（规则同上） |
| `refit_point_consume` | 安装此配件消耗的改装点数 |
| `limb_factor` | 四肢伤害倍率调整 |
| `recoil_multiplier` | 后坐力倍率修饰（同枪械 data） |
| `fire_knockback_power` | 开火击退调整：`addend` / `percent` / `multiplier` |
| `inaccuracy_multiplier` | 连射惩罚调整（`max_stack.addned`、`per_shot.multiplier`） |
| `cooldown_speed` / `cooldown_delay` | 散布回正速度/延迟调整（`addned`，单位与枪 data 一致） |
| `friction` / `gravity` / `bullet_life` | 子弹摩擦、重力、存活时间调整（`addend`） |
| `manual_action_time` / `reload_time` / `sprint_time` | 拉栓、换弹、疾跑时间倍率（数值 1.0 = 不变，`addend` 为加值） |
| `ammo_amount` | 弹容调整（`addend`） |
| `fire_mode_enable` / `fire_mode_disable` | 装上后解锁/禁用指定开火模式 |
| `jump_inaccuracy` | 滞空散布倍率与涨落速度调整 |

## 3. 配件 display 扩展

放在 `assets/<ns>/display/attachments/<配件>.json` 顶层（示例见 `example_attachment_display.json`）。

| 字段 | 用法 |
| --- | --- |
| `group_offset` | 模型组位置偏移：`{ "laser": [2,3,4] }`，单位 1/16 格 |
| `stepless` | 无极变倍（替代原版倍率切换）：`enable`、`zoom_min`、`zoom_max`、`speed`、`zoom_default` |
| `aiming_blur` | 开镜模糊：`aim_blur_enabled`、`aim_blur_range`、`aim_blur_strength`、`aim_blur_far_distance`、`aim_blur_min_factor` |

## 4. 配件动画

### 4.1 display 接线

在配件 display 里加（其余字段保持 TaCZ 原样）：

```json
"taczfixes": {
  "animation": "mypack:my_attachment_animations",     // assets/mypack/animations/my_attachment_animations.animation.json
  "state_machine": "mypack:my_attachment_state_machine", // assets/mypack/scripts/my_attachment_state_machine.lua
  "state_machine_param": { "enable_aim": true }        // 可选，脚本用 context:getStateMachineParams() 读
}
```

### 4.2 动画文件

TaCZ 基岩版动画格式（`format_version` + `animations`）。动画值是在模型基础姿态上的**增量**，不是绝对角度。

### 4.3 Lua 状态机

脚本返回模块表 `M`（示例见 `example_attachment_state_machine.lua`）：

```lua
local M = {}
local idle = {}

function idle.entry(this, context)   -- this = M, context = 状态机上下文
    context:runAnimation("idle", context:getTrack(0, 0), false, LOOP, 0)
end
function idle.update(this, context) end
function idle.transition(this, context, input)
    if input == "shoot" then return this.shoot end
end
M.idle = idle

-- 状态机初始化：分配轨道、读取 display 的 state_machine_param
function M:initialize(context)
    context:ensureTrackLineSize(1)
    context:ensureTracksAmount(0, 2)
    local params = context:getStateMachineParams()
    if params ~= nil and params.enable_aim ~= nil then
        self.enable_aim = params.enable_aim and true or false
    end
end
function M:exit(context) end
-- 初始状态列表（并行状态机，其它状态由 transition 进入）
function M:states() return {self.idle} end

return M
```

- 每个「配件槽位 × 视角（第一/第三人称）」各一份独立脚本实例，模块级变量互不共享。
- 状态表回调：`entry(this, context)` 进入、`update(this, context)` 每帧、`transition(this, context, input)` 返回下一个状态表（`nil` 表示保持）。`this` 是模块表 `M`，自定义字段（常量、开关）都放 `M` 上。
- 轨道：`context:ensureTrackLineSize(n)`、`context:ensureTracksAmount(line, n)`、`context:getTrack(line, track)`（未分配返回 -1）、`context:runAnimation(name, track, blending, playType, transition)`、`context:stopAnimation(track)`、`context:isStopped(track)`；`playType` 用全局常量 `LOOP` / `PLAY_ONCE_STOP` / `PLAY_ONCE_HOLD`。
- 触发：`context:trigger(input)` 把输入交给当前状态的 `transition`。

### 4.4 枪械功能组

动画里可以驱动 TaCZ 枪械的四个功能组（配件模型不必声明这些骨骼，模组会自动补占位骨骼）：

| 组 | 作用 |
| --- | --- |
| `camera` | 镜头旋转，同时作用于第一人称视角和手部 |
| `root` | 整枪平移/旋转/缩放 |
| `lefthand_pos` / `righthand_pos` | 第一人称左右手位置 |

`position` 单位 1/16 格（像素），`rotation` 单位度；动画播放期间叠加到枪械对应组。

完整可运行示例见 `grip_emx_bipod/README.md`。

## 5. 枪械逻辑机（Lua）

- 路径：`assets/<ns>/scripts/<脚本>.lua`；枪 data 里写 `"script": "<ns>:<脚本>"`。
- 脚本返回 table `M`，按需实现下列入口（TaCZ 原生）：

| 入口 | 时机 |
| --- | --- |
| `M.shoot(api)` | 每次开火 |
| `M.start_reload(api)` / `M.tick_reload(api)` / `M.interrupt_reload(api)` | 换弹开始 / 每 tick / 被打断 |
| `M.start_bolt(api)` / `M.tick_bolt(api)` | 拉栓开始 / 每 tick |
| `M.tick_heat(api)` | 过热 tick |
| `M.unload_ammo(api)` | 卸弹 |
| `M.calcSpread(api)` | 计算散布 |
| `M.tick_bullet(api)` | 本模组扩展：该枪发射的每颗子弹每 tick（客户端+服务端都执行），`api` 为子弹 API |

`M.tick_bullet(api)` 的 api：`getTickSinceSpawn()`、`getBulletFacing()`、`getBulletCoordinates()`、`getBulletMotion()` / `setBulletMotion(list)`、`removeThisBullet()`、`getPlayerFacing()` / `getPlayerTarget()` / `getPlayerPosition()` / `getInput()`。

本模组给 `api` 追加的函数（完整说明见 `MixinModernKineticGunScriptAPI` 注释）：

| 函数 | 说明 |
| --- | --- |
| `getMainhandGun()` / `getOffhandGun()` | 主手/副手枪械 id（不是枪械返回 `tacz:empty`） |
| `getLevel()` / `getExp()` / `setExp(v)` | 枪械等级与经验 |
| `getDurability()` / `getDurabilityMax()` / `setDurability(v)` | 耐久 |
| `getChargePower()` / `getChargePowerMax()` / `setChargePower(v)` | 电量（FE） |
| `getEnchantments()` | 附魔列表 `{{id, level}, ...}` |
| `setRecoilMultiplier(v)` | 本枪下次开火后坐力倍率 |
| `getScoreboardValue(name)` / `runCommand(cmd)` | 开火者计分板值 / 以开火者身份执行命令（服务端） |
| `getPlayerFacing()` / `getPlayerTarget()` / `getPlayerPosition()` / `getInput()` | 开火者朝向/视线方块/坐标/按键 |
| `getDualWieldState()` | `"right"` / `"left"` / `"none"` |
| `getScriptParams()` | 枪 data 的 `script_param` 参数表 |
| `tfGetCustomAttachment(slotId)` / `getAttachment(type)` | 自定义槽 / 原生槽配件 id |
| `textShow(value, name)` | 替换语言文件里的 `%name%` 占位符 |
| `setHudParam(key, value)` / `setHudParams(table)` / `clearHudParams()` | 发布自定义 HUD 参数（见第 6 节） |
| `replaceData(dataId)` / `replaceDisplay(displayId)` | 运行时把本枪 data / display 换成指定 id，成功返回 true |

示例见 `example_logic.lua`。

## 6. HTML HUD（ApricityUI）

- 页面放 `assets/<ns>/hud/<页面>.html`，枪 data 里 `"custom_hud": "<ns>:hud/<页面>.html"`。
- 需要安装 ApricityUI；未安装时自动用原版 HUD。
- 使用 `mode=gui` 视口：`<meta name="aui-viewport" content="mode=gui">`。
- Java 端只改写约定 `id` 的元素和 `body` 的 `data-taczfixes-*` 属性，页面里用 CSS 选择器读取。

`body` 上的属性：

| 属性 | 内容 |
| --- | --- |
| `data-taczfixes` | 恒为 `active` |
| `data-taczfixes-gun` | 枪械 id |
| `data-taczfixes-ammo` / `-ammo-max` / `-ammo-percent` / `-ammo-empty` / `-ammo-style` | 弹药数/上限/百分比/空仓/样式 |
| `data-taczfixes-heat` / `-heat-percent` / `-heat-locked` / `-has-heat` | 过热 |
| `data-taczfixes-fire-mode` | 开火模式 id |
| `data-taczfixes-aiming` / `-aim-progress` | 是否瞄准 / 瞄准进度（0-100） |
| `data-taczfixes-reloading` / `-offhand-reloading` | 主/副手换弹 |
| `data-taczfixes-sprinting` / `-hold-breath` / `-dual-wield` / `-crawling` / `-sneaking` / `-show-crosshair` | 其它状态 |

约定 `id`：`ammo-mag`、`ammo-inventory`、`ammo-type`、`ammo-type-icon`、`fire-mode`、`fire-mode-icon`、`gun-hud`、`heat-bar`、`heat-fill`、`heat-text`、`status-*`、`crosshair`；元素上会写入对应 `data-*`（如 `data-value` / `data-max` / `data-percent` / `data-mode` 等），文本类元素会被直接改写文本。

Lua 参数绑定（`setHudParam` 发布）：

| 页面写法 | 效果 |
| --- | --- |
| `body[data-taczfixes-param-key]` | 参数值写进 body 属性，CSS 直接选择 |
| `data-taczfixes-bind="key"` | 元素文本 = 参数值 |
| `data-taczfixes-bind-width="key"` | 元素宽度 = 参数值（%） |

示例见 `example_custom_hud.html`（纯 HTML+CSS，无需 JavaScript）与 `example_logic.lua`。

## 7. 枪械工作台（自动合成 / 电量）

- 工作台方块 data（`data/<ns>/data/blocks/<工作台>.json`）的 `taczfixes` 块：

| 字段 | 说明 |
| --- | --- |
| `power_max` | 电量上限（FE） |
| `consumption` | 自动合成时电量消耗（FE/t） |
| `time_base` / `time_per_type` / `time_per_item` | 自动合成基础时间 / 每种材料时间 / 每件材料时间 |
| `cooldown` | 合成冷却 |

- 配方（`tacz:gun_smith_table_crafting`）的 `taczfixes` 块：

| 字段 | 说明 |
| --- | --- |
| `consumption_manually` | 手动合成电量消耗（FE），不足无法合成 |
| `consumption_auto` | 自动合成额外电量消耗（FE/t），不足暂停并保留进度 |
| `crafting_time` | 合成时间修正（正负皆可） |

示例见 `example_crafting_data.json`、`example_recipe.json`。

## 8. 动态迷彩

`data/<ns>/camo/<迷彩>.json` 声明 + `assets/<ns>/camo/<片段>.glsl` 提供图案：

| 字段 | 说明 |
| --- | --- |
| `name` | 显示名：语言键或纯文本，缺省 `<ns>:<name>` |
| `shader` | 片段位置，推荐 `<ns>:camo/<name>` |
| `strength` | 混色强度 0~1 |
| `emissive` | 自发光 0~1（亮带进泛光） |
| `params` | 最多 3 个 `vec4`，作为 `p0`/`p1`/`p2` 传入 |

片段必须实现 `vec4 taczfixesCamo(vec2 uv, float time, vec4 p0, vec4 p1, vec4 p2)`，不要写 `#version` / `main` / `uniform`；顶层函数名全局唯一。完整示例与写法见 `camo_example_pack/README.md`。

## 9. 调试

- 数据/资源改完按 `F3+T` 重载；脚本报错会写入 `logs/latest.log`（搜索 `taczfixes`）。
- 配件动画脚本运行时报错会停用该实例并回退静态渲染，不影响其它配件。
- 动作/状态机不触发时，先确认脚本里的动画名与动画文件 `animations` 键一致、骨骼名与 geo 一致。
