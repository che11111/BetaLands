# BetaLands 区域保护

[English](AdminRegionProtection.md) · 简体中文

“区域保护”是管理员创建的独立保护区域，不属于普通领地。

- 不计入主领地数量、加入领地数量或等级额度。
- 不参与创建费用、维护费、租金、传送点、银行、成员名额和所有权转移。
- 使用独立模型、独立空间索引和独立持久化表。
- 普通领地可以与区域保护重叠。

## 管理权限

| 权限 | 默认 | 作用 |
| --- | --- | --- |
| `betalands.admin.protect` | OP | 创建、修改、删除区域保护，并管理白名单和保护开关 |

管理员和 OP 仍遵循现有 `protection.op-bypass`、`betalands.bypass` 绕过规则。

“区域保护”的创建、查询、改名、删除、白名单、flag 和边界修改都可以在服务器控制台执行。控制台创建区域或修改边界时必须显式填写 `world`。

控制台示例：

```text
land admin protect create Spawn 2d -100 -100 100 100 world
land admin protect whitelist add Spawn Steve
land admin protect flag Spawn pvp deny
land admin protect list
```

## 创建区域

### 2D

2D 区域忽略 Y 坐标，保护整个世界高度的指定 X/Z 范围。

```text
/land admin protect create <名称> 2d <x1> <z1> <x2> <z2> [world]
```

示例：

```text
/land admin protect create Spawn 2d -100 -100 100 100
```

### 3D

3D 区域保护指定的 X/Y/Z 体积，包含最小和最大边界。

```text
/land admin protect create <名称> 3d <x1> <y1> <z1> <x2> <y2> <z2> [world]
```

示例：

```text
/land admin protect create Bank 3d 20 50 30 60 100 40 world
```

规则：

- 坐标顺序会自动归一化。
- 玩家执行时省略 `world` 使用玩家当前世界。
- 控制台执行时必须指定世界。
- 区域名称长度为 2–32，只能包含字母、数字、下划线和连字符。
- 不同区域保护之间禁止重叠；同一 X/Z 范围只有 3D Y 范围完全分离时才允许。
- 区域保护可以与普通玩家领地重叠。

## 管理区域

```text
/land admin protect list
/land admin protect info <名称>
/land admin protect rename <名称> <新名称>
/land admin protect delete <名称>
```

## 修改边界

修改不会立即生效：

```text
/land admin protect resize <名称> 2d <x1> <z1> <x2> <z2> [world]
/land admin protect resize <名称> 3d <x1> <y1> <z1> <x2> <y2> <z2> [world]
```

执行后聊天栏会同时输出旧边界和新边界，并提示确认：

```text
/land admin protect resize confirm <名称>
/land admin protect resize cancel <名称>
```

确认请求 2 分钟后自动失效。确认时会再次检查新边界是否与其他区域保护重叠。

## 白名单

```text
/land admin protect whitelist add <区域> <玩家> [权限]
/land admin protect whitelist remove <区域> <玩家>
/land admin protect whitelist list <区域>
```

不填写权限时，默认授予：

```text
landcreate, landclaim, build, break, use, container, pvp
```

多个权限可用逗号或空格分隔。例如 `/land admin protect whitelist add Spawn Alice build,break` 只授予放置和破坏权限。再次添加同一玩家会替换原有权限。如果填写的权限全部无法识别，指令会授予整套默认权限，指定部分权限时请核对拼写。

权限含义：

| 权限 | 作用 |
| --- | --- |
| `landcreate` | 在区域内允许执行 `/land create` |
| `landclaim` | 在区域内允许执行 `/land claim`、自动圈地和 `/land subcreate` |
| `build` | 放置方块、倒出桶内液体 |
| `break` | 破坏方块、用空桶取液体 |
| `use` | 门、按钮、拉杆等交互 |
| `container` | 打开箱子、熔炉等容器 |
| `pvp` | 区域内 PVP |
| `all` | 全部操作权限 |

白名单权限只解除“区域保护”拦截，不会突破普通领地权限。

## 与普通领地重叠

在保护范围内放置、破坏方块或交互时，BetaLands 先检查区域权限，再检查普通领地权限。

| 操作位置及对应权限 | 区域保护 | 普通领地权限 |
| --- | --- | --- |
| 在区域内且具有对应白名单权限 | 放行 | 继续检查 |
| 在区域内但缺少对应白名单权限 | 拒绝 | — |
| 不在区域保护内 | 不参与 | 使用原有规则 |

普通领地与管理区域部分重叠时按操作类型分别处理：

- `/land add`、`/land kick`、`/land perm`、改名、转让、删除、维护豁免、银行、租金和传送点等管理操作不受区域保护拦截，仍按普通领地权限处理。
- 在重叠区域内放置、破坏和交互时，玩家必须拥有区域保护白名单中的对应权限。玩家直接攻击时，区域层在白名单授予 `pvp` 权限或区域的有效 `pvp` 开关为 `allow` 时放行。
- 通过区域保护检查后，玩家仍必须满足普通领地权限。
- 白名单内的普通成员不能越过原领地的 `manage` 权限。
- 区域外的普通领地部分不受区域保护影响。

例如玩家 A 的领地 AAA 只有一半位于区域保护内：

- A 即使不在区域白名单，也可以正常执行 `/land add B`、`/land kick`、`/land perm` 等管理操作。
- A、B 或任何其他玩家操作 AAA 与区域保护重叠的部分时，必须先满足区域白名单权限。
- A、B 操作 AAA 在区域保护外的部分时，只检查普通领地权限。

## 圈地限制

以下操作会检查区域保护：

- `/land create`
- `/land claim`
- `/land claim auto`
- `/land subcreate`

`/land create` 在玩家当前位置检查 `landcreate` 权限。手动和自动圈地扩展区块时，在玩家当前位置检查 `landclaim` 权限。`/land subcreate` 会在 3D 选区相交的每个区域保护中检查 `landclaim`。原有等级额度、费用、主领地间距、重叠和欠费规则仍按各指令执行。

## 保护开关

```text
/land admin protect flag <区域> <flag> <allow|deny|default>
```

区域保护会应用以下开关：

```text
explosion, fire, fluid, pvp, mobs, trampling
```

`default` 使用全局保护配置。区域 flag 只解除或增加区域保护层的限制，不会突破普通领地规则。

指令也接受并保存 `tp`。目前传送指令读取普通领地的传送设置。

## 地图

`plugins/BetaLands/config.yml`:

```yaml
maps:
  show-admin-regions: true
  colors:
    dynmap:
      admin-region: "#8F82A8"
    bluemap:
      admin-region: "#8F82A8"
```

Dynmap 和 BlueMap 使用独立标记组“区域保护”，默认使用低饱和度灰紫色 `#8F82A8`。

BlueMap 将 2D 区域显示为平面标记，将 3D 区域显示为立体标记。Dynmap 显示平面区域标记，并记录 3D 区域的 Y 范围。

地图信息卡显示区域名称、ID、类型、世界、边界、白名单人数、白名单成员及权限、非白名单权限和创建时间。白名单成员名后没有括号表示拥有全部七项默认权限。括号内的权限是该成员拥有的权限子集，空权限集显示为“无权限”。非白名单的 PVP 权限取决于区域的有效 `pvp` 开关。创建人可通过 `/land admin protect info <名称>` 查询。

## 只读 API

API 版本为 `v7`：

```java
Optional<AdminRegionInfo> getAdminRegion(String nameOrId);
Optional<AdminRegionInfo> getAdminRegionAt(
        String worldName, int x, int y, int z);
List<AdminRegionInfo> getAdminRegions();
boolean canUseAdminRegion(String playerName, String nameOrId,
                          AdminRegionPermission permission);
```

请在服务器主线程调用这些方法。`canUseAdminRegion` 返回区域白名单是否授予指定权限。实际操作还会检查管理员绕过、相关保护开关和普通领地权限。
