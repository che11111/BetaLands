# BetaLands Admin Protected Regions

English · [简体中文](AdminRegionProtection-zh.md)

“Admin Protected Regions” are independent protection areas created by administrators. They are not regular lands.

- They do not count toward main-land limits, joined-land limits, or level quotas.
- They do not participate in creation fees, maintenance, rent, teleport points, land banks, family-member limits, or ownership transfer.
- They use a separate model, spatial index, and persistence tables.
- Regular lands may overlap an admin protected region.

## Management Permission

| Permission | Default | Purpose |
| --- | --- | --- |
| `betalands.admin.protect` | OP | Create, modify, and delete protected regions; manage whitelists and protection flags |

Administrators and OPs continue to follow the existing `protection.op-bypass` and `betalands.bypass` rules.

Protected-region creation, queries, renaming, deletion, whitelists, flags, and boundary changes may all be run from the server console. When creating a region or changing its bounds from the console, `world` must be specified explicitly.

Console examples:

```text
land admin protect create Spawn 2d -100 -100 100 100 world
land admin protect whitelist add Spawn Steve
land admin protect flag Spawn pvp deny
land admin protect list
```

## Creating a Region

### 2D

A 2D region ignores Y coordinates and protects the specified X/Z area across the entire world height.

```text
/land admin protect create <name> 2d <x1> <z1> <x2> <z2> [world]
```

Example:

```text
/land admin protect create Spawn 2d -100 -100 100 100
```

### 3D

A 3D region protects the specified X/Y/Z volume, including the minimum and maximum boundaries.

```text
/land admin protect create <name> 3d <x1> <y1> <z1> <x2> <y2> <z2> [world]
```

Example:

```text
/land admin protect create Bank 3d 20 50 30 60 100 40 world
```

Rules:

- Coordinate order is normalized automatically.
- When a player omits `world`, the player's current world is used.
- The world is required when the command is run from the console.
- Region names are 2–32 characters long and may contain only letters, numbers, underscores, and hyphens.
- Admin protected regions cannot overlap each other. A shared X/Z footprint is allowed only when their 3D Y ranges are completely separate.
- Admin protected regions may overlap regular player lands.

## Managing Regions

```text
/land admin protect list
/land admin protect info <name>
/land admin protect rename <name> <new-name>
/land admin protect delete <name>
```

## Resizing a Region

Changes are not applied immediately:

```text
/land admin protect resize <name> 2d <x1> <z1> <x2> <z2> [world]
/land admin protect resize <name> 3d <x1> <y1> <z1> <x2> <y2> <z2> [world]
```

The chat output shows both the previous and new bounds and asks for confirmation:

```text
/land admin protect resize confirm <name>
/land admin protect resize cancel <name>
```

A confirmation request expires after two minutes. Confirming checks again for overlap with other admin regions before applying the change.

## Whitelist

```text
/land admin protect whitelist add <region> <player> [permissions]
/land admin protect whitelist remove <region> <player>
/land admin protect whitelist list <region>
```

When permissions are omitted, the following operational permissions are granted:

```text
landcreate, landclaim, build, break, use, container, pvp
```

Separate permissions with commas or spaces. For example, `/land admin protect whitelist add Spawn Alice build,break` grants only `build` and `break`. Adding an existing player replaces their permission set. Check the spelling when assigning a subset because the command grants the default set if it recognizes none of the supplied permissions.

Permission meanings:

| Permission | Purpose |
| --- | --- |
| `landcreate` | Allow `/land create` inside the region |
| `landclaim` | Allow `/land claim`, auto-claim, and `/land subcreate` inside the region |
| `build` | Place blocks and empty buckets |
| `break` | Break blocks and fill buckets |
| `use` | Interact with doors, buttons, levers, and similar blocks |
| `container` | Open chests, furnaces, and other containers |
| `pvp` | Allow PVP inside the region |
| `all` | Grant every operational permission |

Whitelist permissions only bypass the admin-region protection layer. They do not override regular land permissions.

## Overlap with Regular Lands

For block changes and interactions inside the protected bounds, BetaLands checks admin-region permissions before regular land permissions.

| Location and permission for the action | Admin-region check | Regular land permission |
| --- | --- | --- |
| Inside with the required whitelist permission | Pass | Continue checking |
| Inside without the required whitelist permission | Deny | — |
| Outside the admin region | Not involved | Existing rules apply |

When a regular land partially overlaps an admin protected region, actions are handled according to their type:

- `/land add`, `/land kick`, `/land perm`, rename, transfer, delete, maintenance exemption, bank, rent, and teleport-point management are not intercepted by admin-region protection and continue to use normal land permissions.
- Placing, breaking, and interacting inside the overlapping region require the corresponding admin-region whitelist permission. Direct PVP requires either the whitelist's `pvp` permission or an effective region `pvp` flag of `allow`.
- After passing the admin-region check, normal land permissions still apply.
- A whitelisted regular member still cannot bypass the land's normal `manage` permission.
- The portion of the regular land outside the admin region is unaffected by admin-region protection.

For example, if player A's land AAA is only half inside an admin protected region:

- A may use `/land add B`, `/land kick`, and `/land perm` normally even without an admin-region whitelist entry.
- A, B, or any other player must satisfy admin-region whitelist permissions to operate on the overlapping part.
- A and B operate on the non-overlapping part using normal land permissions only.

## Claim Restrictions

Admin-region checks apply to:

- `/land create`
- `/land claim`
- `/land claim auto`
- `/land subcreate`

`/land create` checks `landcreate` at the player's current position. Manual and automatic chunk claims check `landclaim` at the player's position when claiming the chunk. `/land subcreate` checks `landclaim` in every admin region the 3D selection intersects. The usual quotas, fees, main-land gaps, overlap checks, and debt restrictions still apply where relevant.

## Protection Flags

```text
/land admin protect flag <region> <flag> <allow|deny|default>
```

Region protection applies these flags:

```text
explosion, fire, fluid, pvp, mobs, trampling
```

`default` uses the global protection configuration. An admin-region flag can only modify the region protection layer and cannot bypass regular land rules.

The command also accepts and stores `tp`. Teleport commands currently consult the regular land's teleport settings.

## Maps

Dynmap and BlueMap use the independent “区域保护” marker group with the low-saturation gray-purple `#8F82A8`.

BlueMap renders 2D regions as flat outlines and 3D regions as volumes. Dynmap uses area overlays and includes Y bounds for 3D regions.

The map card shows the region name, ID, type, world, bounds, whitelist count, whitelist members and permissions, non-whitelist permissions, and creation time. A whitelist name with no parentheses has all seven default permissions. A name followed by a permission list has that subset. The card labels entries with empty permission sets as “no permissions”. The non-whitelist PVP entry follows the effective region `pvp` flag. Use `/land admin protect info <name>` to look up the creator.

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

## Read-only API

The API version is `v7`:

```java
Optional<AdminRegionInfo> getAdminRegion(String nameOrId);
Optional<AdminRegionInfo> getAdminRegionAt(
        String worldName, int x, int y, int z);
List<AdminRegionInfo> getAdminRegions();
boolean canUseAdminRegion(String playerName, String nameOrId,
                          AdminRegionPermission permission);
```

Call these methods on the server main thread. `canUseAdminRegion` reports whether the region's whitelist grants the specified permission. Runtime checks also consider administrator bypass, relevant protection flags, and regular land permissions.
