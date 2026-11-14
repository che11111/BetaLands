package dev.che1s.betalands.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandMaintenanceService;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.util.Text;

public final class BetaLandsApiImpl implements BetaLandsApi {
    private final BetaLandsPlugin plugin;

    public BetaLandsApiImpl(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public double getServerMaintenanceBankBalance() {
        return plugin.getLandManager().getServerMaintenanceBank();
    }

    @Override
    public Optional<LandInfo> getLand(String nameOrId) {
        Land land = plugin.getLandManager().resolveRegion(nameOrId, null);
        return land == null ? Optional.<LandInfo>empty() : Optional.of(snapshot(land));
    }

    @Override
    public Optional<LandInfo> getLandAt(String worldName, int x, int y, int z) {
        Land land = plugin.getLandManager().findLandAt(worldName, x, y, z);
        return land == null ? Optional.<LandInfo>empty() : Optional.of(snapshot(land));
    }

    @Override
    public List<LandInfo> getLands() {
        List<LandInfo> result = new ArrayList<LandInfo>();
        for (Land land : plugin.getLandManager().getLands()) {
            result.add(snapshot(land));
        }
        sortLands(result);
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<LandInfo> getLandsOwnedBy(String playerName) {
        List<LandInfo> result = new ArrayList<LandInfo>();
        if (playerName != null) {
            for (Land land : plugin.getLandManager().getLands()) {
                if (land.isOwner(playerName)) {
                    result.add(snapshot(land));
                }
            }
        }
        sortLands(result);
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<PlayerLandAccess> getPlayerAccesses(String playerName) {
        List<PlayerLandAccess> result = new ArrayList<PlayerLandAccess>();
        if (playerName == null || playerName.length() == 0) {
            return Collections.emptyList();
        }
        for (Land land : plugin.getLandManager().getLands()) {
            Set<LandPermission> permissions = permissions(playerName, land);
            if (permissions.isEmpty()) {
                continue;
            }
            result.add(access(playerName, land, permissions));
        }
        Collections.sort(result, new Comparator<PlayerLandAccess>() {
            @Override
            public int compare(PlayerLandAccess first, PlayerLandAccess second) {
                return first.getLand().getDisplayName()
                        .compareToIgnoreCase(second.getLand().getDisplayName());
            }
        });
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<PlayerLandAccess> findLandsWithPermission(String playerName,
                                                          LandPermission permission) {
        List<PlayerLandAccess> result = new ArrayList<PlayerLandAccess>();
        if (playerName == null || playerName.length() == 0 || permission == null) {
            return Collections.emptyList();
        }
        for (Land land : plugin.getLandManager().getLands()) {
            dev.che1s.betalands.model.LandPermission internal = internal(permission);
            if (plugin.getLandManager().hasPermissionInLand(
                    playerName, land, internal, new java.util.HashSet<String>())) {
                result.add(access(playerName, land, permissions(playerName, land)));
            }
        }
        Collections.sort(result, new Comparator<PlayerLandAccess>() {
            @Override
            public int compare(PlayerLandAccess first, PlayerLandAccess second) {
                return first.getLand().getDisplayName()
                        .compareToIgnoreCase(second.getLand().getDisplayName());
            }
        });
        return Collections.unmodifiableList(result);
    }

    private PlayerLandAccess access(String playerName, Land land,
                                    Set<LandPermission> permissions) {
        LandManager manager = plugin.getLandManager();
        String key = Text.key(playerName);
        boolean rentPayer = land.isRentEnabled()
                && manager.getRentPayers(land).contains(key);
        RentAccount account = manager.getRentAccount(playerName);
        LandMaintenanceService maintenance = plugin.getMaintenanceService();
        Land main = manager.getFamilyMain(land);
        return new PlayerLandAccess(playerName, snapshot(land),
                land.isOwner(playerName), rentPayer, permissions,
                rentPayer ? land.getRentAmount() : 0.0D,
                account == null ? 0.0D : account.getBalance(),
                maintenance == null ? 0.0D : maintenance.getMaintenanceFee(land),
                main == null ? 0.0D : main.getBankBalance(),
                maintenance == null ? 0L : maintenance.getNextRentAt(land),
                maintenance == null ? 0L : maintenance.getNextDueAt(land));
    }

    private Set<LandPermission> permissions(String playerName, Land land) {
        EnumSet<LandPermission> result = EnumSet.noneOf(LandPermission.class);
        for (LandPermission permission : LandPermission.values()) {
            if (plugin.getLandManager().hasPermissionInLand(playerName, land,
                    internal(permission), new java.util.HashSet<String>())) {
                result.add(permission);
            }
        }
        return result;
    }

    private LandInfo snapshot(Land land) {
        LandManager manager = plugin.getLandManager();
        Land main = manager.getFamilyMain(land);
        LevelProfile profile = plugin.getLevels().getProfile(main);
        LandMaintenanceService maintenance = plugin.getMaintenanceService();
        List<LandInfo.AreaInfo> areas = new ArrayList<LandInfo.AreaInfo>();
        for (Rect2i area : land.getAreas()) {
            areas.add(new LandInfo.AreaInfo(area.getMinX(), area.getMinZ(),
                    area.getMaxX(), area.getMaxZ()));
        }
        HomePoint homePoint = land.getHome();
        LandInfo.HomeInfo home = homePoint == null ? null
                : new LandInfo.HomeInfo(homePoint.getX(), homePoint.getY(),
                homePoint.getZ(), homePoint.getYaw(), homePoint.getPitch());
        EnumMap<dev.che1s.betalands.api.LandFlag, Boolean> flags =
                new EnumMap<dev.che1s.betalands.api.LandFlag, Boolean>(
                        dev.che1s.betalands.api.LandFlag.class);
        EnumMap<dev.che1s.betalands.api.LandFlag, String> sources =
                new EnumMap<dev.che1s.betalands.api.LandFlag, String>(
                        dev.che1s.betalands.api.LandFlag.class);
        for (dev.che1s.betalands.model.LandFlag flag
                : dev.che1s.betalands.model.LandFlag.values()) {
            dev.che1s.betalands.api.LandFlag apiFlag = api(flag);
            flags.put(apiFlag, Boolean.valueOf(manager.getEffectiveLandFlag(land, flag)));
            sources.put(apiFlag, manager.getLandFlagSource(land, flag));
        }

        return new LandInfo(land.getId(), land.getName(), land.getDisplayName(),
                land.getOwner(), land.getWorld(), land.getParentId(),
                main == null || land.isSubclaim() ? (main == null ? null : main.getName()) : null,
                land.isSubclaim(), land.inheritsParentPermissions(),
                land.getCreatedAt(), land.getSource(), land.getMinY(), land.getMaxY(),
                land.area(), land.volume(), manager.getOccupiedChunks(land),
                level(profile), land.isSubclaim() && main != null
                        ? main.getBankBalance() : land.getBankBalance(),
                maintenance == null ? 0.0D : maintenance.getMaintenanceFee(land),
                land.isSubclaim() && main != null
                        ? main.isMaintenanceExempt() : land.isMaintenanceExempt(),
                land.isSubclaim() && main != null
                        ? main.getLastMaintenanceAt() : land.getLastMaintenanceAt(),
                maintenance == null ? 0L : maintenance.getNextDueAt(land),
                land.isRentEnabled(), land.getRentAmount(), land.getLastRentAt(),
                maintenance == null ? 0L : maintenance.getNextRentAt(land),
                areas, home, flags, sources, members(land), rentAccounts(land),
                subclaims(land));
    }

    private List<LandInfo.MemberInfo> members(Land land) {
        List<LandInfo.MemberInfo> result = new ArrayList<LandInfo.MemberInfo>();
        String ownerKey = Text.key(land.getOwner());
        result.add(new LandInfo.MemberInfo(land.getOwner(), land.hasMember(ownerKey),
                true, EnumSet.allOf(LandPermission.class)));
        for (String key : plugin.getLandManager().getFamilyMemberKeys(land)) {
            result.add(new LandInfo.MemberInfo(displayName(key),
                    land.getMemberPermissions(key) != null, land.isOwner(key),
                    permissions(key, land)));
        }
        return result;
    }

    private List<LandInfo.RentAccountInfo> rentAccounts(Land land) {
        List<LandInfo.RentAccountInfo> result = new ArrayList<LandInfo.RentAccountInfo>();
        Set<String> keys = new java.util.LinkedHashSet<String>(
                plugin.getLandManager().getRentPayers(land));
        for (Map.Entry<String, RentAccount> entry
                : plugin.getLandManager().getRentAccounts().entrySet()) {
            if (land.getId().equals(entry.getValue().getDebtLandId())) {
                keys.add(entry.getKey());
            }
        }
        for (String playerKey : keys) {
            RentAccount account = plugin.getLandManager().getRentAccount(playerKey);
            if (account != null) {
                result.add(new LandInfo.RentAccountInfo(
                        account.getPlayerName(), account.getBalance()));
            }
        }
        Collections.sort(result, new Comparator<LandInfo.RentAccountInfo>() {
            @Override
            public int compare(LandInfo.RentAccountInfo first,
                               LandInfo.RentAccountInfo second) {
                return first.getPlayerName().compareToIgnoreCase(second.getPlayerName());
            }
        });
        return result;
    }

    private List<LandInfo.SubclaimInfo> subclaims(Land land) {
        List<LandInfo.SubclaimInfo> result = new ArrayList<LandInfo.SubclaimInfo>();
        if (land.isSubclaim()) {
            return result;
        }
        for (Land child : plugin.getLandManager().getChildren(land.getId())) {
            result.add(new LandInfo.SubclaimInfo(child.getId(), child.getName(),
                    child.getOwner()));
        }
        Collections.sort(result, new Comparator<LandInfo.SubclaimInfo>() {
            @Override
            public int compare(LandInfo.SubclaimInfo first, LandInfo.SubclaimInfo second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        return result;
    }

    private LandInfo.LevelInfo level(LevelProfile profile) {
        return new LandInfo.LevelInfo(profile.getId(), profile.getDisplayName(),
                profile.getMaxMainLands(), profile.getMaxMainChunks(),
                profile.getMaxSubclaimsPerMain(),
                profile.getMaxFamilyMembers(), profile.getMaxJoinedLands(),
                profile.getCreateMainPrice(), profile.getClaimChunkPrice(),
                profile.getClaimChunkIncrement(), profile.getSubclaimPerBlockPrice(),
                profile.getMaintenancePerChunkPerCycle(),
                profile.getMaintenancePerMemberPerCycle(),
                profile.getMaintenanceIntervalDays());
    }

    private String displayName(String key) {
        PlayerData data = plugin.getLandManager().getPlayerData(key);
        return data == null || data.getLastName() == null
                || data.getLastName().length() == 0 ? key : data.getLastName();
    }

    private void sortLands(List<LandInfo> lands) {
        Collections.sort(lands, new Comparator<LandInfo>() {
            @Override
            public int compare(LandInfo first, LandInfo second) {
                return first.getDisplayName().compareToIgnoreCase(second.getDisplayName());
            }
        });
    }

    private dev.che1s.betalands.model.LandPermission internal(LandPermission permission) {
        return dev.che1s.betalands.model.LandPermission.valueOf(permission.name());
    }

    private LandPermission api(dev.che1s.betalands.model.LandPermission permission) {
        return LandPermission.valueOf(permission.name());
    }

    private dev.che1s.betalands.api.LandFlag api(
            dev.che1s.betalands.model.LandFlag flag) {
        return dev.che1s.betalands.api.LandFlag.valueOf(flag.name());
    }
}
