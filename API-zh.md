# BetaLands API

BetaLands 通过 Bukkit `ServicesManager` 注册只读查询 API：

```text
dev.che1s.betalands.api.BetaLandsApi
```

当前 API 版本为 `6`。API 返回不可变快照，不提供创建、删除、转移、修改成员或修改经济数据的写入接口。

## 1. 接入

### 1.1 插件依赖

如果其他插件必须依赖 BetaLands，可在自己的 `plugin.yml` 中使用：

```yaml
depend: [BetaLands]
```

如果 BetaLands 只是可选集成：

```yaml
softdepend: [BetaLands]
```

可选集成仍然需要在运行时检查插件和 API 是否存在。

### 1.2 获取 API

```java
package example.myplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import dev.che1s.betalands.api.BetaLandsApi;

public final class MyPlugin extends JavaPlugin {
    private BetaLandsApi betaLandsApi;

    @Override
    public void onEnable() {
        Plugin betaLands = Bukkit.getPluginManager().getPlugin("BetaLands");
        if (betaLands == null || !betaLands.isEnabled()) {
            return;
        }

        betaLandsApi = BetaLandsApi.get();
        if (betaLandsApi == null) {
            getLogger().warning("BetaLands API is unavailable.");
            return;
        }
        if (betaLandsApi.getApiVersion() < 6) {
            getLogger().warning("BetaLands API 6 or newer is required.");
            betaLandsApi = null;
        }
    }
}
```

也可以直接通过 ServicesManager 获取：

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import dev.che1s.betalands.api.BetaLandsApi;

RegisteredServiceProvider<BetaLandsApi> registration =
        Bukkit.getServicesManager().getRegistration(BetaLandsApi.class);
BetaLandsApi api = registration == null ? null : registration.getProvider();
```

编译时请使用 BetaLands JAR 作为 `provided` 或普通编译依赖。不要把这些 API 类重新打包或 shade 到消费者插件中。

### 1.3 生命周期

- BetaLands 未启用时，`BetaLandsApi.get()` 返回 `null`。
- BetaLands 关闭时会注销 API 服务。
- 推荐在消费者插件的 `onEnable()` 中获取 API，并缓存接口引用。
- 使用 `softdepend` 时，BetaLands 未安装属于正常情况，不应抛出异常。
- API 返回的是查询时快照；领地或经济数据改变后需要重新调用查询方法。

## 2. 通用行为

### 2.1 线程

API 必须在 Minecraft 服务器主线程调用。领地索引、成员、保护和经济数据不是线程安全的内存快照服务。

不要在异步任务中直接调用 API。如需异步处理，应先在主线程取得快照，再把结果交给异步线程。

### 2.2 活动领地

API 只返回活动领地。使用软删除删除的领地不会出现在：

- `getLand(...)`
- `getLandAt(...)`
- `getLands()`
- `getLandsOwnedBy(...)`
- `getPlayerAccesses(...)`
- `findLandsWithPermission(...)`

已软删除领地的旧 ID 和旧名称也不能通过 API 查询。

### 2.3 空值

- 查询不到领地时返回 `Optional.empty()`。
- 玩家名为 `null` 或空字符串时返回空列表。
- `findLandsWithPermission(..., null)` 返回空列表。
- API 返回的列表和集合不可修改。
- `LandInfo.getParentId()`、`getParentName()` 和 `getHome()` 可能为 `null`。
- 其他列表字段不会返回 `null`，没有数据时返回空列表。

### 2.4 数据单位

| 数据 | 单位 |
| --- | --- |
| 坐标 | Minecraft 方块坐标 |
| `area` | 水平方块数 |
| `volume` | 3D 方块数 |
| `occupiedChunks` | 唯一 16×16 区块数量 |
| 金额 | iConomy 的金额单位，Java `double` |
| 时间 | `System.currentTimeMillis()` 毫秒时间戳 |

`AreaInfo.getMaxX()` 和 `getMaxZ()` 是包含边界的最大值。

## 3. 查询方法

```java
public interface BetaLandsApi {
    int getApiVersion();

    Optional<LandInfo> getLand(String nameOrId);

    Optional<LandInfo> getLandAt(
            String worldName, int x, int y, int z);

    List<LandInfo> getLands();

    List<LandInfo> getLandsOwnedBy(String playerName);

    List<PlayerLandAccess> getPlayerAccesses(String playerName);

    List<PlayerLandAccess> findLandsWithPermission(
            String playerName, LandPermission permission);

    double getServerMaintenanceBankBalance();
}
```

### 3.1 `getLand`

```java
Optional<LandInfo> land = api.getLand("AAA");
Optional<LandInfo> subclaim = api.getLand("AAA.shop");
Optional<LandInfo> byId = api.getLand("550e8400-e29b-41d4-a716-446655440000");
```

支持：

- 主领地名称，例如 `AAA`
- 子领地完整名称，例如 `AAA.shop`
- 领地 ID，即 UUID 字符串
- 兼容的旧式 `owner:AAA` 查询格式

名称查询不区分大小写。

### 3.2 `getLandAt`

```java
Optional<LandInfo> result = api.getLandAt(
        "world", blockX, blockY, blockZ);
```

返回指定坐标命中的活动领地：

1. 命中子领地时优先返回子领地。
2. 没有子领地命中时返回主领地。
3. 没有活动领地时返回 `Optional.empty()`。

世界名称匹配不区分大小写。子领地会检查 Y 轴范围；主领地按 2D 水平范围判断。

### 3.3 `getLands`

返回全部活动主领地和活动子领地，按 `getDisplayName()` 不区分大小写排序。

返回列表不可修改。这是完整快照，不适合每 tick 调用。

### 3.4 `getLandsOwnedBy`

```java
List<LandInfo> owned = api.getLandsOwnedBy("Alice");
```

返回该玩家作为最高领地主的活动主领地和子领地。结果按显示名称排序。

如果子领地的领地主与主领地不同，该子领地会按它自己的 `owner` 判断。

### 3.5 `getPlayerAccesses`

```java
List<PlayerLandAccess> accesses = api.getPlayerAccesses("Alice");
```

返回该玩家在当前领地中拥有至少一项有效权限的所有活动领地。

有效权限包括：

- 玩家是领地主时获得的全部权限
- 直接授予的成员权限
- `inheritsParentPermissions()` 为 `true` 时从主领地继承的权限

没有任何有效权限的领地不会出现在结果中。

### 3.6 `findLandsWithPermission`

```java
List<PlayerLandAccess> buildable =
        api.findLandsWithPermission("Alice", LandPermission.BUILD);
```

返回玩家在对应领地中实际拥有指定权限的所有活动领地。领地主隐式拥有全部权限，因此结果也会包含领地主拥有的领地。

该方法使用最终有效权限，也就是会考虑权限继承。

### 3.7 `getServerMaintenanceBankBalance`

返回 BetaLands 内部记录的服务端维护费累计银行。

该值不是 iConomy 账户余额，也不是服务器上已经可以提取的 iConomy 资金。

## 4. 枚举

### 4.1 `LandPermission`

| 值 | 含义 |
| --- | --- |
| `BUILD` | 放置方块、倒桶 |
| `BREAK` | 破坏方块、盛桶 |
| `USE` | 门、按钮、拉杆等交互 |
| `CONTAINER` | 箱子、熔炉、发射器等容器 |
| `RENT` | 承担该领地每日租金 |
| `PVP` | 在领地内对其他玩家造成伤害 |
| `MANAGE` | 管理成员、权限、保护开关和传送点 |

领地主隐式拥有全部权限，不需要单独的成员权限记录。

### 4.2 `LandFlag`

| 值 | 含义 |
| --- | --- |
| `EXPLOSION` | 爆炸是否允许影响领地 |
| `FIRE` | 火焰是否允许点燃或破坏领地 |
| `FLUID` | 液体是否允许跨领地流动 |
| `PVP` | 领地内玩家战斗开关 |
| `TP` | 非成员公众传送开关 |
| `MOBS` | 自然生物生成开关 |
| `TRAMPLING` | 玩家和生物踩踏开关 |

`LandInfo.getFlags()` 返回的是最终有效值，已经应用以下优先级：

```text
子领地覆盖值 > 主领地覆盖值 > 全局默认值
```

## 5. `LandInfo`

`LandInfo` 是一个不可变领地快照。

### 5.1 身份与层级

| 方法 | 说明 |
| --- | --- |
| `getId()` | 不可变领地 ID，UUID 字符串 |
| `getName()` | 内部名称；子领地不包含主领地前缀 |
| `getDisplayName()` | 完整名称，例如 `AAA` 或 `AAA.shop` |
| `getOwner()` | 当前最高领地主 |
| `getWorld()` | 领地世界名称 |
| `getParentId()` | 主领地 ID；主领地返回 `null` |
| `getParentName()` | 父主领地内部名称；主领地返回 `null` |
| `isSubclaim()` | 是否为 3D 子领地 |
| `inheritsParentPermissions()` | 是否继承父领地成员权限 |
| `getSource()` | 来源，例如 `manual` 或 Towny 迁移来源 |
| `getCreatedAt()` | 创建时间，毫秒时间戳 |

### 5.2 几何数据

| 方法 | 说明 |
| --- | --- |
| `getMinY()` / `getMaxY()` | 最小和最大 Y；对子领地有意义 |
| `getArea()` | 所有水平区域面积之和 |
| `getVolume()` | 3D 体积；主领地等于水平面积，子领地包含 Y 轴高度 |
| `getOccupiedChunks()` | 水平范围覆盖的唯一 16×16 区块数 |
| `getAreas()` | 水平区域列表 |
| `getHome()` | 显式领地传送点；可能为 `null` |

`getHome() == null` 不等于领地不能传送。BetaLands 在内部还可能使用领地中心或地表高度作为传送回退位置，但 API 只暴露显式保存的出生点。

`AreaInfo`：

| 方法 | 说明 |
| --- | --- |
| `getMinX()` / `getMaxX()` | X 轴包含式边界 |
| `getMinZ()` / `getMaxZ()` | Z 轴包含式边界 |

`HomeInfo`：

| 方法 | 说明 |
| --- | --- |
| `getX()` / `getY()` / `getZ()` | 传送点坐标 |
| `getYaw()` / `getPitch()` | 水平朝向和俯仰角 |

### 5.3 等级与价格

`getLevel()` 返回 `LevelInfo`。查询子领地时，等级来自所属主领地及其领地主，而不是子领地独立配置。

| 方法 | 说明 |
| --- | --- |
| `getId()` / `getDisplayName()` | 等级 ID 和显示名称 |
| `getMaxMainLands()` | 最多主领地数量 |
| `getMaxMainChunks()` | 每个主领地最多区块数 |
| `getMaxSubclaimsPerMain()` | 每个主领地最多子领地数 |
| `getMaxFamilyMembers()` | 领地家庭最多成员数 |
| `getMaxJoinedLands()` | 玩家最多加入领地数 |
| `getCreateMainPrice()` | 创建主领地价格 |
| `getClaimChunkPrice()` | 扩展基础价格 |
| `getClaimChunkIncrement()` | 按现有区块数递增的价格 |
| `getSubclaimPerBlockPrice()` | 子领地每方块价格 |
| `getMaintenancePerChunkPerCycle()` | 每区块每维护周期费用 |
| `getMaintenancePerMemberPerCycle()` | 每成员每维护周期费用 |
| `getMaintenanceIntervalDays()` | 维护费结算间隔天数 |

`LandInfo.getNextExpansionPrice()`：

```text
claimChunkPrice + claimChunkIncrement × occupiedChunks
```

结果四舍五入到 `0.01`。该方法只对主领地扩展有意义；子领地不能通过区块扩展，返回值不应作为子领地操作依据。即使返回了价格，也不代表当前一定满足额度、欠费、相邻区块或权限条件。

### 5.4 经济字段

| 方法 | 说明 |
| --- | --- |
| `getBankBalance()` | 领地家庭银行原始余额，可为负数 |
| `getMaintenanceDebt()` | 欠费金额，非负；余额不是负数时为 `0` |
| `getMaintenancePrepaidBalance()` | 预缴维护余额，非负；余额不是正数时为 `0` |
| `getMaintenancePerCycle()` | 当前家庭每维护周期费用 |
| `isMaintenanceExempt()` | 是否维护费豁免 |
| `getLastMaintenanceAt()` | 上次维护结算时间 |
| `getNextMaintenanceAt()` | 下次维护结算时间 |
| `isRentEnabled()` | 当前领地是否启用每日租金 |
| `getRentAmount()` | 当前领地记录的每日租金额 |
| `getLastRentAt()` | 上次租金处理时间 |
| `getNextRentAt()` | 下次租金处理时间 |

查询子领地时，`getBankBalance()`、维护豁免、维护时间和等级都会映射到所属主领地及其家庭。

`getRentAmount()` 不代表一定正在收费。只有 `isRentEnabled() == true` 且玩家拥有有效 `RENT` 权限时，该玩家才会承担租金。

### 5.5 成员与保护开关

| 方法 | 说明 |
| --- | --- |
| `getMembers()` | 领地主和家庭成员列表 |
| `getFlags()` | 所有保护开关的最终有效值 |
| `getFlagSources()` | 每个开关的来源名称或 `global default` |
| `isFlagEnabled(LandFlag)` | 指定开关是否允许 |
| `getSubclaims()` | 当前主领地的直接子领地摘要 |
| `getRentAccounts()` | 当前租金承担者和欠费归属玩家的账户摘要 |

`MemberInfo`：

| 方法 | 说明 |
| --- | --- |
| `getPlayerName()` | 玩家显示名称或存储名称 |
| `isOwner()` | 是否为当前领地最高领地主 |
| `isDirectMember()` | 当前领地是否有直接成员记录 |
| `getPermissions()` | 玩家在当前领地中的最终有效权限 |

`getMembers()` 的领地主项会始终包含全部权限。其他成员可能通过父领地继承获得权限，因此 `isDirectMember()` 为 `false` 时仍可能拥有权限。

`getSubclaims()` 只可能用于主领地；查询子领地时返回空列表。

`SubclaimInfo`：

| 方法 | 说明 |
| --- | --- |
| `getId()` | 子领地 ID |
| `getName()` | 子领地内部名称 |
| `getOwner()` | 子领地最高领地主 |

`RentAccountInfo`：

| 方法 | 说明 |
| --- | --- |
| `getPlayerName()` | 玩家名称 |
| `getBalance()` | 玩家全局租金账户余额 |
| `isInDebt()` | 余额是否小于 `0` |

## 6. `PlayerLandAccess`

`PlayerLandAccess` 将一名玩家和一个领地的访问、权限与费用合并为一个快照。

| 方法 | 说明 |
| --- | --- |
| `getPlayerName()` | 查询时传入的玩家名 |
| `getLand()` | 对应的 `LandInfo` |
| `isOwner()` | 玩家是否为该领地最高领地主 |
| `isRentPayer()` | 玩家当前是否为该领地的租金承担者 |
| `getPermissions()` | 当前领地中的最终有效权限 |
| `hasPermission(LandPermission)` | 是否拥有指定有效权限 |
| `getDailyRent()` | 当前玩家在该领地承担的每日租金；不是承担者时为 `0` |
| `getRentBalance()` | 当前玩家全服共享租金账户余额 |
| `isRentInDebt()` | 全局租金账户是否欠费 |
| `getFamilyMaintenancePerCycle()` | 该领地家庭每维护周期费用总额 |
| `getMaintenancePrepaidBalance()` | 所属主领地银行原始余额，可能为负数 |
| `getNextRentAt()` | 当前领地下次租金处理时间 |
| `getNextMaintenanceAt()` | 当前领地家庭下次维护结算时间 |

注意：

- 每个玩家只有一个全服租金账户，所以同一个人的多个 `PlayerLandAccess.getRentBalance()` 会返回同一个值。
- `getDailyRent()` 只表示当前领地对该玩家的每日应收租金，不是 iConomy 实时余额。
- `getMaintenancePrepaidBalance()` 名称表示家庭维护账户，但数值可能为负数。需要正数预缴额时请先判断 `> 0`，或使用 `LandInfo.getMaintenancePrepaidBalance()`。

## 7. 经济账本语义

BetaLands 的领地银行和租金账户是插件内部账本，不是 iConomy 实时账户镜像。

### 7.1 维护费

- 主领地银行是维护费预缴账户。
- 正数是预缴余额，负数是欠费。
- 每个维护周期的维护费按主领地区块数和家庭成员数计算。
- 家庭成员数按主领地及全部直接子领地去重：排除主领地领地主，只统计在主领地或任一子领地中拥有有效 `BUILD` 权限的玩家；没有有效 `BUILD` 权限的成员不计费。
- 达到等级配置的结算间隔后，从领地银行扣除。
- 实际扣除成功的金额累计到 BetaLands 服务端维护银行。

### 7.2 租金

- 每个玩家只有一个全局租金账户。
- 只有拥有有效 `RENT` 权限的成员承担租金。
- 领地主不承担自己领地的租金。
- 多个领地同时扣费时，按每日租金从低到高处理。
- 余额不足时，在导致负数的领地停止后续扣费，并把欠费归到该领地。
- 只有实际覆盖的金额会进入对应领地家庭银行。

玩家的 iConomy 余额只在明确执行存款时使用，不会自动作为租金或维护费预缴余额。

## 8. 示例

### 8.1 查询坐标上的领地

```java
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.che1s.betalands.api.BetaLandsApi;
import dev.che1s.betalands.api.LandInfo;

public LandInfo getLandAt(BetaLandsApi api, Player player) {
    Location location = player.getLocation();
    Optional<LandInfo> result = api.getLandAt(
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    return result.orElse(null);
}
```

### 8.2 查询玩家可建造领地

```java
import java.util.List;

import org.bukkit.entity.Player;

import dev.che1s.betalands.api.BetaLandsApi;
import dev.che1s.betalands.api.LandPermission;
import dev.che1s.betalands.api.PlayerLandAccess;

public List<PlayerLandAccess> getBuildable(
        BetaLandsApi api, Player player) {
    List<PlayerLandAccess> buildable = api.findLandsWithPermission(
            player.getName(), LandPermission.BUILD);

    for (PlayerLandAccess access : buildable) {
        String landName = access.getLand().getDisplayName();
        boolean owner = access.isOwner();
        boolean inRentDebt = access.isRentInDebt();
    }
    return buildable;
}
```

### 8.3 检查保护和欠费状态

```java
import java.util.Optional;

import dev.che1s.betalands.api.BetaLandsApi;
import dev.che1s.betalands.api.LandFlag;
import dev.che1s.betalands.api.LandInfo;

public void inspectLand(BetaLandsApi api) {
    Optional<LandInfo> result = api.getLand("AAA");
    if (result.isPresent()) {
        LandInfo land = result.get();
        boolean pvpAllowed = land.isFlagEnabled(LandFlag.PVP);
        double maintenanceDebt = land.getMaintenanceDebt();
        double prepaid = land.getMaintenancePrepaidBalance();
    }
}
```

## 9. 性能与限制

- `getLands()`、`getLandsOwnedBy()`、`getPlayerAccesses()` 和 `findLandsWithPermission()` 会遍历活动领地并生成快照。
- 权限查询还会计算继承关系和有效权限。
- 不要在每 tick、每次方块事件或高频移动事件中调用全量查询。
- 高频使用时应缓存结果，并在需要时主动刷新。
- API 快照不会自动更新，也不会触发消费者插件回调。
- 当前没有领地创建、删除、成员变化、保护开关变化或经济变化事件。
- 当前 API 是只读的，不能通过 API 修改 BetaLands 数据。
- 不要依赖 `BetaLandsApiImpl`、内部 `model`、`service` 或存储类。

## 10. 版本兼容

当前 API 版本：

```java
BetaLandsApi.API_VERSION == 6
```

调用方应在初始化时检查：

```java
if (api.getApiVersion() < 6) {
    // 当前代码依赖 API 6
}
```

API 6 将维护费字段改为“每周期金额”。旧的 `getDailyMaintenance()`、`getFamilyDailyMaintenance()` 和 `getMaintenancePer*PerDay()` 方法暂时保留，但已经标记为过时，并返回每周期金额。

只使用 `dev.che1s.betalands.api` 包中的公开类型。内部实现可能随插件版本变化，不构成兼容承诺。
