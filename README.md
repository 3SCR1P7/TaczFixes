# TaCZ Fixes

[Timeless and Classics Zero](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) 的附属模组。
在 TaCZ 1.2.0 的基础上添加机制，并加入大量面向枪包作者的数据驱动功能；也有不少功能开箱即用。

## 版本

| 分支 | Minecraft | 加载器 | 模组版本 | 对应 TaCZ |
| --- | --- | --- | --- | --- |
| `taczfixes` | 1.20.1 | Forge 47.4+ | 2.2.0 | tacz-forge 1.20.1 1.2.0 |
| `taczfixes-1.21.1` | 1.21.1 | NeoForge 21.1+ | 3.0.0 | tacz-neoforge 1.21.1 1.2.0 |

客户端与服务端需要安装相同版本的 TaCZ Fixes（网络协议版本 `2`），单机/客户端安装即可使用全部功能。

## 依赖

必需：

- Timeless and Classics Zero 1.2.0

可选（缺失时对应功能自动降级，不会崩溃）：

- **Cloth Config** 11.x —— 提供游戏内配置界面
- **ApricityUI** 1.3.1 —— 枪包自定义 HTML HUD

## 功能

### 改装与配件

- 自定义配件槽位（数据驱动，枪包可自由定义槽位/黑白名单/适配器）
- 虚拟配件：不占背包、不消耗物品的配件安装方式
- 改装点数系统：安装消耗点数、剩余点数显示、超支拒绝
- 改装方案：保存 / 加载 / 导入导出改装码
- 改装配色、改装界面搜索与分页、视野缩放、槽位尺寸、位置微调
- 更多配件属性：弹匣容量、有效射程、摩擦力、重力、冲刺/装填/拉栓时间等 tooltip 展示

### 枪械机制

- 四肢伤害倍率（按枪械类型与命中部位）、爬行/潜行命中阈值
- 后坐力倍率、散布倍率与连射散布惩罚、跳跃散布
- 跳弹、子弹穿透玻璃、动态结构（Create）子弹碰撞
- 枪械耐久、铁砧/附魔兼容、枪械等级与击杀经验
- 自定义开火模式（全自动/三连发/半自动等）、连射惩罚
- 开火阻挡（近身枪口被推开的机制）、水下阻止射击
- 工程台自动合成（消耗 FE，可配置时长与功率）
- 枪械附魔系统（30+ 附魔：力量/穿刺/连爆/吸血/双发/斩首等）

### 视觉与镜头

- PBR 反射、泛光（Bloom）、自发光、附魔光效
- 开火动态光照（枪口/子弹/爆炸）
- 瞄具无级变倍、开镜景深、开镜打断疾跑、屏息
- 压制效果：受弹时模糊、暗角、色散
- 动态准星、枪械移动滚转与旋转惯性

### 双持

- 双持手枪：副手独立开火 / 装填 / 拉栓 / 切换开火模式 / 近战 / 举盾
- 单臂瞄准（副手可用）、第三人称持枪动画
- 副手弹药 HUD、热量条

### 耐力与爬行

- 瞄准耐力与体力系统（含屏息消耗、重量影响、低耐力晃动）
- ParCool 联动：滑铲/爬行消耗与 HUD 整合
- 爬行姿势兼容：TaCZ 爬行与 ParCool 爬行均可正常开火/装填，不再重置配件动画

### 动画

- 配件独立 Lua 动画状态机（`example/grip_emx_bipod` 为两脚架完整示例）
- 配件动画在开火、装填、匍匐、主副手切换时保持稳定

### HUD

- 枪包自定义 HTML HUD（ApricityUI）
- 副手枪械 HUD、耐力条、瞄准耐力条

### 枪包管理

- 枪包列表管理、排序、前缀/版本校验与不兼容提示
- CurseForge 内搜索 / 下载 / 更新枪包，启动时自动检查更新并显示红点
- 旧格式枪包自动转换、补全 `gunpack.meta.json`
- 手持枪械/配件数据编辑（写回来源枪包）

### 兼容

- Create、Accelerated Rendering、LesRaisins Tactical Equipements、Iron's Spellbooks、ProjectE
- ParCool、Peek、TouhouLittleMaid、YesSteveModel、Embeddium

## 按键

| 默认按键 | 功能 |
| --- | --- |
| `Alt + S` | 打开配置界面（需 Cloth Config） |
| `Alt + G` | 枪包管理 |
| `U` | 编辑手持枪械/配件数据 |
| `B` | 切换瞄具 |
| `Left Ctrl` | 屏息 |
| `X` | 双持单臂瞄准（开镜时只举当前手） |
| `Y` | 切换副手开火模式 |

## 配置

配置位于 `config/taczfixes/*.toml`，按模块分为 20 个文件，例如：

`gun_level`、`limb_damage_multiplier`、`recoil_modifier`、`inaccuracy_modifier`、`bullet_ricochet`、`recoil_knockback`、`gun_light`、`gun_spell`、`stepless_zoom`、`dynamic_crosshair`、`refit`、`auto_craft`、`blocking`、`compat`、`gun_enchantment`、`aiming_stamina`、`stamina`、`dual_wield`、`visual_effect`、`suppression`

配置文件中带注释，修改后重新加载资源或重启生效；服务端配置会同步到客户端。

## 数据目录

- `taczfixes_cache/`：枪械/配件 data 覆写、改装方案、CurseForge 搜索缓存与安装记录
- `apricity/taczfixes/<namespace>/hud/*.html`：由模组从枪包自动同步，**请勿手动修改**
- `tacz/`：枪包放置目录（zip 或文件夹）

## 枪包作者

- HTML HUD 放在 `assets/<namespace>/hud/**.html`，模组会自动同步给 ApricityUI
- 配件槽位、虚拟配件、改装点数、附魔兼容等均通过 data json 定义
- 配件动画示例见 `example/`：`grip_emx_bipod` 为可用的两脚架状态机与动画配置

## 构建

```bash
# 需要 libs/ 下已备好离线依赖
./gradlew build --offline
```

产物位于 `build/libs/taczfixes-<version>.jar`。

## 许可

MIT License · 作者 Ssscript
