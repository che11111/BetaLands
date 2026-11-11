# BetaLands API

BetaLands registers a read-only query API through Bukkit's `ServicesManager`:

```text
dev.che1s.betalands.api.BetaLandsApi
```

The current API version is `5`. The API returns immutable snapshots and provides no write operations for creating, deleting, transferring, editing members, or modifying economic data.

## 1. Integration

### 1.1 Plugin Dependency

If another plugin requires BetaLands, add this to its `plugin.yml`:

```yaml
depend: [BetaLands]
```

If BetaLands is an optional integration:

```yaml
softdepend: [BetaLands]
```

Optional integrations must still check whether the plugin and API are available at runtime.

### 1.2 Obtaining the API

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
        if (betaLandsApi.getApiVersion() < 5) {
            getLogger().warning("BetaLands API 5 or newer is required.");
            betaLandsApi = null;
        }
    }
}
```

You can also query the ServicesManager directly:

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import dev.che1s.betalands.api.BetaLandsApi;

RegisteredServiceProvider<BetaLandsApi> registration =
        Bukkit.getServicesManager().getRegistration(BetaLandsApi.class);
BetaLandsApi api = registration == null ? null : registration.getProvider();
```

Use the BetaLands JAR as a `provided` or regular compile-time dependency. Do not package or shade these API classes into the consuming plugin.

### 1.3 Lifecycle

- `BetaLandsApi.get()` returns `null` when BetaLands is not enabled.
- BetaLands unregisters the API service when it disables.
- Obtain the API during the consuming plugin's `onEnable()` and cache the interface reference.
- When using `softdepend`, it is normal for BetaLands to be absent.
- API results are snapshots. Query again after land or economic data changes.

## 2. General Behavior

### 2.1 Threading

The API must be called on the Minecraft server main thread. The underlying land indexes, membership data, protection data, and economic data do not provide a thread-safe snapshot service.

Do not call the API directly from an asynchronous task. If asynchronous processing is required, obtain the snapshot on the main thread first and pass it to the asynchronous worker.

### 2.2 Active Lands

The API returns active lands only. Soft-deleted lands do not appear in:

- `getLand(...)`
- `getLandAt(...)`
- `getLands()`
- `getLandsOwnedBy(...)`
- `getPlayerAccesses(...)`
- `findLandsWithPermission(...)`

The old ID or old name of a soft-deleted land cannot be queried through the API.

### 2.3 Nulls

- An unknown land returns `Optional.empty()`.
- A `null` or empty player name returns an empty list.
- `findLandsWithPermission(..., null)` returns an empty list.
- Returned lists and sets are unmodifiable.
- `LandInfo.getParentId()`, `getParentName()`, and `getHome()` may return `null`.
- Other list fields do not return `null`; they return an empty list when no data exists.

### 2.4 Units

| Data | Unit |
| --- | --- |
| Coordinates | Minecraft block coordinates |
| `area` | Horizontal block count |
| `volume` | 3D block count |
| `occupiedChunks` | Number of unique 16×16 chunks |
| Money | iConomy amount unit, represented as a Java `double` |
| Time | `System.currentTimeMillis()` timestamp in milliseconds |

`AreaInfo.getMaxX()` and `getMaxZ()` are inclusive maximum boundaries.

## 3. Query Methods

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

Accepted values:

- Main-land name, such as `AAA`
- Full subclaim name, such as `AAA.shop`
- Land ID, a UUID string
- Legacy-compatible `owner:AAA` lookup format

Name matching is case-insensitive.

### 3.2 `getLandAt`

```java
Optional<LandInfo> result = api.getLandAt(
        "world", blockX, blockY, blockZ);
```

Returns the active land at the specified position:

1. A matching subclaim takes priority.
2. If no subclaim matches, a matching main land is returned.
3. If no active land matches, `Optional.empty()` is returned.

World-name matching is case-insensitive. Subclaims check their Y range; main lands use their 2D horizontal coverage.

### 3.3 `getLands`

Returns every active main land and active subclaim, sorted by `getDisplayName()` in case-insensitive order.

The returned list is unmodifiable. This is a full snapshot and should not be called every tick.

### 3.4 `getLandsOwnedBy`

```java
List<LandInfo> owned = api.getLandsOwnedBy("Alice");
```

Returns active main lands and subclaims for which the player is the highest owner. Results are sorted by display name.

If a subclaim has a different owner from its main land, ownership is determined by the subclaim's own `owner`.

### 3.5 `getPlayerAccesses`

```java
List<PlayerLandAccess> accesses = api.getPlayerAccesses("Alice");
```

Returns every active land where the player has at least one effective permission.

Effective permissions include:

- All permissions when the player is the land owner
- Directly granted member permissions
- Permissions inherited from the main land when `inheritsParentPermissions()` is `true`

Lands where the player has no effective permission are omitted.

### 3.6 `findLandsWithPermission`

```java
List<PlayerLandAccess> buildable =
        api.findLandsWithPermission("Alice", LandPermission.BUILD);
```

Returns every active land where the player effectively has the requested permission. Land owners implicitly have every permission, so lands owned by the player are included.

This method uses final effective permissions and therefore accounts for inheritance.

### 3.7 `getServerMaintenanceBankBalance`

Returns BetaLands' internal accumulated server-maintenance ledger.

This value is not an iConomy account balance and is not withdrawable iConomy funds held by the server.

## 4. Enums

### 4.1 `LandPermission`

| Value | Meaning |
| --- | --- |
| `BUILD` | Place blocks and empty buckets |
| `BREAK` | Break blocks and fill buckets |
| `USE` | Doors, buttons, levers, and similar interactions |
| `CONTAINER` | Chests, furnaces, dispensers, and similar containers |
| `RENT` | Pay daily rent for the land |
| `PVP` | Damage other players inside the land |
| `MANAGE` | Manage members, permissions, protection flags, and the spawn |

The land owner implicitly has every permission and does not need a separate member permission record.

### 4.2 `LandFlag`

| Value | Meaning |
| --- | --- |
| `EXPLOSION` | Whether explosions may affect the land |
| `FIRE` | Whether fire may ignite or damage the land |
| `FLUID` | Whether fluids may cross land boundaries |
| `PVP` | Player combat flag for the land |
| `TP` | Public teleport access for non-members |
| `MOBS` | Natural mob spawning |
| `TRAMPLING` | Player and mob trampling |

`LandInfo.getFlags()` returns final effective values using this precedence:

```text
subclaim override > main-land override > global default
```

## 5. `LandInfo`

`LandInfo` is an immutable land snapshot.

### 5.1 Identity and Hierarchy

| Method | Description |
| --- | --- |
| `getId()` | Immutable land ID, a UUID string |
| `getName()` | Internal name; a subclaim name does not include the main-land prefix |
| `getDisplayName()` | Full name, such as `AAA` or `AAA.shop` |
| `getOwner()` | Current highest owner |
| `getWorld()` | Land world name |
| `getParentId()` | Main-land ID; `null` for a main land |
| `getParentName()` | Internal name of the parent main land; `null` for a main land |
| `isSubclaim()` | Whether this is a 3D subclaim |
| `inheritsParentPermissions()` | Whether parent member permissions are inherited |
| `getSource()` | Source, such as `manual` or a Towny migration source |
| `getCreatedAt()` | Creation time in milliseconds |

### 5.2 Geometry

| Method | Description |
| --- | --- |
| `getMinY()` / `getMaxY()` | Minimum and maximum Y; primarily relevant to subclaims |
| `getArea()` | Sum of all horizontal areas |
| `getVolume()` | 3D volume; a main land equals its horizontal area, while a subclaim includes Y height |
| `getOccupiedChunks()` | Number of unique 16×16 chunks covered horizontally |
| `getAreas()` | Horizontal area list |
| `getHome()` | Explicit land spawn; may be `null` |

`getHome() == null` does not mean the land is impossible to teleport to. BetaLands may internally fall back to the land center and surface height, but the API exposes only the explicitly saved spawn.

`AreaInfo`:

| Method | Description |
| --- | --- |
| `getMinX()` / `getMaxX()` | Inclusive X boundaries |
| `getMinZ()` / `getMaxZ()` | Inclusive Z boundaries |

`HomeInfo`:

| Method | Description |
| --- | --- |
| `getX()` / `getY()` / `getZ()` | Spawn coordinates |
| `getYaw()` / `getPitch()` | Horizontal rotation and pitch |

### 5.3 Level and Pricing

`getLevel()` returns `LevelInfo`. For a subclaim, the level comes from its main land and owner, not from a separate subclaim configuration.

| Method | Description |
| --- | --- |
| `getId()` / `getDisplayName()` | Level ID and display name |
| `getMaxMainLands()` | Maximum main lands |
| `getMaxMainChunks()` | Maximum chunks per main land |
| `getMaxSubclaimsPerMain()` | Maximum subclaims per main land |
| `getMaxFamilyMembers()` | Maximum family members |
| `getMaxJoinedLands()` | Maximum lands a player may join |
| `getCreateMainPrice()` | Main-land creation price |
| `getClaimChunkPrice()` | Base expansion price |
| `getClaimChunkIncrement()` | Price increment per existing chunk |
| `getSubclaimPerBlockPrice()` | Subclaim price per block |
| `getMaintenancePerChunkPerDay()` | Daily maintenance per chunk |
| `getMaintenancePerMemberPerDay()` | Daily maintenance per member |
| `getMaintenanceIntervalDays()` | Maintenance charging interval |

`LandInfo.getNextExpansionPrice()`:

```text
claimChunkPrice + claimChunkIncrement × occupiedChunks
```

The result is rounded to `0.01`. This method is meaningful only for expanding a main land; subclaims cannot be expanded by chunk. A returned price does not guarantee that quotas, debt checks, adjacency, or permissions currently allow expansion.

### 5.4 Economic Fields

| Method | Description |
| --- | --- |
| `getBankBalance()` | Raw family bank balance; may be negative |
| `getMaintenanceDebt()` | Non-negative debt amount; `0` when the balance is not negative |
| `getMaintenancePrepaidBalance()` | Non-negative prepaid maintenance; `0` when the balance is not positive |
| `getDailyMaintenance()` | Current family daily maintenance |
| `isMaintenanceExempt()` | Whether maintenance is exempt |
| `getLastMaintenanceAt()` | Last maintenance charge time |
| `getNextMaintenanceAt()` | Next maintenance charge time |
| `isRentEnabled()` | Whether daily rent is enabled for this land |
| `getRentAmount()` | Stored daily rent for this land |
| `getLastRentAt()` | Last rent processing time |
| `getNextRentAt()` | Next rent processing time |

For a subclaim, `getBankBalance()`, maintenance exemption, maintenance timing, and level map to the parent main land and its family.

`getRentAmount()` does not mean rent is currently being charged. A player pays only when `isRentEnabled() == true` and the player has effective `RENT` permission.

### 5.5 Members and Protection Flags

| Method | Description |
| --- | --- |
| `getMembers()` | Land owner and family member list |
| `getFlags()` | Final effective values for all protection flags |
| `getFlagSources()` | Source name for each flag, or `global default` |
| `isFlagEnabled(LandFlag)` | Whether a specific flag allows the action |
| `getSubclaims()` | Direct subclaim summaries for a main land |
| `getRentAccounts()` | Current rent payers and the player holding the debt attribution |

`MemberInfo`:

| Method | Description |
| --- | --- |
| `getPlayerName()` | Player display name or stored name |
| `isOwner()` | Whether the player is the current highest owner |
| `isDirectMember()` | Whether a direct member record exists on this land |
| `getPermissions()` | Final effective permissions on this land |

The owner entry in `getMembers()` always contains all permissions. Other members may inherit permissions from a parent land, so a member can have permissions even when `isDirectMember()` is `false`.

`getSubclaims()` is non-empty only for main lands. It returns an empty list when querying a subclaim.

`SubclaimInfo`:

| Method | Description |
| --- | --- |
| `getId()` | Subclaim ID |
| `getName()` | Internal subclaim name |
| `getOwner()` | Subclaim highest owner |

`RentAccountInfo`:

| Method | Description |
| --- | --- |
| `getPlayerName()` | Player name |
| `getBalance()` | Player global rent-account balance |
| `isInDebt()` | Whether the balance is below `0` |

## 6. `PlayerLandAccess`

`PlayerLandAccess` combines one player's access, permissions, and fees for one land into a snapshot.

| Method | Description |
| --- | --- |
| `getPlayerName()` | Player name used for the query |
| `getLand()` | Corresponding `LandInfo` |
| `isOwner()` | Whether the player is the land's highest owner |
| `isRentPayer()` | Whether the player currently pays rent in this land |
| `getPermissions()` | Final effective permissions on this land |
| `hasPermission(LandPermission)` | Whether the player has a specific effective permission |
| `getDailyRent()` | Daily rent charged to this player on this land; `0` when not a rent payer |
| `getRentBalance()` | Player's server-wide shared rent-account balance |
| `isRentInDebt()` | Whether the global rent account is in debt |
| `getFamilyDailyMaintenance()` | Total daily maintenance for the land family |
| `getMaintenancePrepaidBalance()` | Raw parent main-land bank balance; may be negative |
| `getNextRentAt()` | Next rent processing time for this land |
| `getNextMaintenanceAt()` | Next maintenance time for this land family |

Notes:

- Each player has one server-wide rent account. Multiple `PlayerLandAccess.getRentBalance()` values for the same player therefore contain the same balance.
- `getDailyRent()` is the daily rent due for this land only. It is not the player's live iConomy balance.
- `getMaintenancePrepaidBalance()` refers to the family maintenance account but may be negative. To obtain a positive prepaid amount, check `> 0` or use `LandInfo.getMaintenancePrepaidBalance()`.

## 7. Economic Ledger Semantics

BetaLands' land banks and rent accounts are internal plugin ledgers, not live mirrors of iConomy accounts.

### 7.1 Maintenance

- A main-land bank is the maintenance prepayment account.
- A positive balance is prepaid maintenance; a negative balance is debt.
- Daily maintenance is calculated from main-land chunk count and family member count.
- The land bank is charged when the configured level interval is reached.
- Amounts successfully collected accumulate in BetaLands' server-maintenance bank.

### 7.2 Rent

- Each player has one global rent account.
- Only members with effective `RENT` permission pay rent.
- The owner never pays rent for their own land.
- When several lands charge together, they are processed from lowest daily rent to highest.
- If the balance is insufficient, processing stops at the land that causes the negative balance, and that land receives the debt attribution.
- Only amounts actually covered by the account are credited to the corresponding land-family bank.

The player's iConomy balance is used only when the player explicitly deposits money. It is not automatically treated as rent or maintenance prepayment.

## 8. Examples

### 8.1 Query the Land at a Position

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

### 8.2 Query Lands Where a Player Can Build

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

### 8.3 Check Protection and Debt State

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

## 9. Performance and Limitations

- `getLands()`, `getLandsOwnedBy()`, `getPlayerAccesses()`, and `findLandsWithPermission()` iterate active lands and create snapshots.
- Permission queries also calculate inheritance and effective permissions.
- Do not call full-list queries every tick, for every block event, or for high-frequency movement events.
- Cache results for frequent use and refresh them when needed.
- API snapshots do not update automatically and do not notify the consuming plugin.
- There are currently no land creation, deletion, membership, protection-flag, or economic change events.
- The API is read-only and cannot modify BetaLands data.
- Do not depend on `BetaLandsApiImpl`, internal `model`, `service`, or storage classes.

## 10. Version Compatibility

Current API version:

```java
BetaLandsApi.API_VERSION == 5
```

Consumers should check it during initialization:

```java
if (api.getApiVersion() < 5) {
    // This code depends on API 5.
}
```

Use only public types from the `dev.che1s.betalands.api` package. Internal implementation may change between plugin versions and is not covered by compatibility guarantees.
