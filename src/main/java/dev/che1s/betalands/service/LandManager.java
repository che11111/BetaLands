package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.model.Rect2i;

public final class LandManager {
    private final BetaLandsPlugin plugin;
    private final LandIndex index = new LandIndex();
    private final LandImportMerger importMerger = new LandImportMerger(index);
    private final PlayerSessionService sessions;
    private final FinanceService finance;
    private final AutoClaimService autoClaims;
    private final PermissionService permissions;
    private final AccessPolicy accessPolicy;
    private final MembershipService membership;
    private final ClaimService claims;
    private final LandOwnershipService ownership;
    private final LandInvitationService invitations = new LandInvitationService();

    public LandManager(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.sessions = new PlayerSessionService(plugin, this);
        this.finance = new FinanceService();
        this.autoClaims = new AutoClaimService(this);
        this.permissions = new PermissionService(plugin, this);
        this.accessPolicy = new AccessPolicy(plugin, this, permissions);
        this.membership = new MembershipService(plugin, this, permissions,
                accessPolicy);
        this.claims = new ClaimService(plugin, this, permissions);
        this.ownership = new LandOwnershipService(plugin, this, permissions);
    }

    public void clear() {
        index.clear();
        sessions.clear();
        finance.clear();
        autoClaims.clear();
        invitations.clear();
    }

    public Map<String, PlayerData> getPlayers() {
        return sessions.getPlayers();
    }

    public LandInvitationService getInvitations() {
        return invitations;
    }

    public double getServerMaintenanceBank() {
        return finance.getServerMaintenanceBank();
    }

    public void setServerMaintenanceBank(double serverMaintenanceBank) {
        finance.setServerMaintenanceBank(serverMaintenanceBank);
    }

    public void addServerMaintenanceBank(double amount) {
        finance.addServerMaintenanceBank(amount);
    }

    public Map<String, RentAccount> getRentAccounts() {
        return finance.getRentAccounts();
    }

    public RentAccount getRentAccount(String playerName) {
        return finance.getRentAccount(playerName);
    }

    public RentAccount getOrCreateRentAccount(String playerName) {
        return finance.getOrCreateRentAccount(playerName);
    }

    public void syncPlayerLevel(Player player) {
        sessions.syncPlayerLevel(player);
    }

    public void applyLevelToOwnedLands(String playerName, LevelProfile profile) {
        sessions.applyLevelToOwnedLands(playerName, profile);
    }

    public Land getById(String id) {
        return index.get(id);
    }

    public Land getByIdIncludingDeleted(String id) {
        return index.getIncludingDeleted(id);
    }

    public List<Land> getLands() {
        return index.getAll();
    }

    public List<Land> getDeletedLands() {
        return index.getDeletedLands();
    }

    public List<Land> getPersistedLands() {
        return index.getPersistedLands();
    }

    public List<Land> getOwnedMainLands(String owner) {
        return index.getOwnedMainLands(owner);
    }

    public List<Land> getAccessibleLands(String playerName) {
        List<Land> result = new ArrayList<Land>(
                index.getOwnedLands(playerName));
        for (Land land : index.getMemberLands(playerName)) {
            if (!result.contains(land) && !land.isOwner(playerName)) {
                result.add(land);
            }
        }
        return result;
    }

    public List<Land> getMemberLands(String playerName) {
        return index.getMemberLands(playerName);
    }

    void refreshMemberIndex(Land land) {
        index.refreshMembership(land);
    }

    public List<Land> getChildren(String parentId) {
        return index.getChildren(parentId);
    }

    public Land addLoadedLand(Land land) {
        if (land == null || land.getId() == null || land.getId().length() == 0) {
            return null;
        }
        if (index.contains(land.getId())) {
            throw new IllegalArgumentException("Duplicate land id: " + land.getId());
        }
        Land duplicateName = findByName(land.getTerritoryName());
        if (duplicateName != null && duplicateName != land) {
            throw new IllegalArgumentException("Duplicate land name: " + land.getTerritoryName());
        }
        index.add(land);
        return land;
    }

    void reindexAfterMutation() {
        index.reindexAfterMutation();
    }

    public void rebuildIndexes() {
        index.reindexAfterMutation();
    }

    void indexClaimedChunk(Land land, int chunkX, int chunkZ) {
        index.indexClaimedChunk(land, chunkX, chunkZ);
    }

    void archiveLand(Land land) {
        index.archive(land);
    }

    public Land findLandAt(String worldName, int x, int y, int z) {
        return index.findLandAt(worldName, x, y, z);
    }

    public Land findLandAt(Block block) {
        return findLandAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Land findLandAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return findLandAt(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Land resolveRegion(String reference, String actingPlayer) {
        return index.resolveRegion(reference);
    }

    public Land findByName(String name) {
        return index.findByName(name);
    }

    public boolean isTerritoryNameAvailable(String name) {
        return findByName(name) == null;
    }

    public CreateResult createMain(Player owner, String name) {
        return claims.createMain(owner, name);
    }

    public CreateResult createMain(Player owner, String name, boolean force) {
        return claims.createMain(owner, name, force);
    }

    public CreateResult claimChunk(Player actor, Land land) {
        return claims.claimChunk(actor, land);
    }

    public CreateResult claimChunk(Player actor, Land land, boolean force) {
        return claims.claimChunk(actor, land, force);
    }

    public String unclaimChunk(Player actor, Land land) {
        return claims.unclaimChunk(actor, land);
    }

    static List<Rect2i> subtractChunk(List<Rect2i> areas, Rect2i removed) {
        return ClaimService.subtractChunk(areas, removed);
    }

    public List<Land> getClaimCandidates(Player player) {
        return claims.getClaimCandidates(player);
    }

    public Land getOwnedParentAtPlayer(Player player) {
        return claims.getOwnedParentAtPlayer(player);
    }

    public Land getAutoClaimTarget(Player player) {
        return autoClaims.getTarget(player);
    }

    public boolean isAutoClaimEnabled(Player player) {
        return autoClaims.isEnabled(player);
    }

    public void startAutoClaim(Player player, Land land) {
        autoClaims.start(player, land);
    }

    public void startAutoClaim(Player player, Land land, boolean force) {
        autoClaims.start(player, land, force);
    }

    public void stopAutoClaim(Player player) {
        autoClaims.stop(player);
    }

    public ClaimResult tryAutoClaim(Player player) {
        return autoClaims.tryClaim(player);
    }

    public ClaimResult tryAutoClaim(Player player, boolean force) {
        return autoClaims.tryClaim(player, force);
    }

    public Land findMainAtChunk(String worldName, int chunkX, int chunkZ) {
        return index.findMainAtChunk(worldName, chunkX, chunkZ);
    }

    boolean landContainsChunk(Land land, int chunkX, int chunkZ) {
        return index.landContainsChunk(land, chunkX, chunkZ);
    }

    boolean isChunkAdjacentToLand(Land land, int chunkX, int chunkZ) {
        return index.isChunkAdjacentToLand(land, chunkX, chunkZ);
    }

    public static Rect2i chunkBounds(int blockX, int blockZ) {
        return ClaimService.chunkBounds(blockX, blockZ);
    }

    public static int countOccupiedChunks(List<Rect2i> areas) {
        return ClaimService.countOccupiedChunks(areas);
    }

    public static double calculateClaimPrice(LevelProfile profile, long currentChunks) {
        return ClaimService.calculateClaimPrice(profile, currentChunks);
    }

    public static boolean exceedsMainChunkLimit(LevelProfile profile, int currentChunks) {
        return ClaimService.exceedsMainChunkLimit(profile, currentChunks);
    }

    public CreateResult createSubclaim(Player actor, Land parent, String name) {
        return claims.createSubclaim(actor, parent, name);
    }

    public CreateResult createSubclaim(Player actor, Land parent, String name,
                                       boolean force) {
        return claims.createSubclaim(actor, parent, name, force);
    }

    public int countMainLands(String owner) {
        return index.countMainLands(owner);
    }

    public boolean canManage(Player player, Land land) {
        return permissions.canManage(player, land);
    }

    public boolean canEditLandFlags(Player player, Land land) {
        return permissions.canEditLandFlags(player, land);
    }

    public boolean getEffectiveLandFlag(Land land, LandFlag flag) {
        return permissions.effectiveFlag(land, flag);
    }

    public String getLandFlagSource(Land land, LandFlag flag) {
        return permissions.flagSource(land, flag);
    }

    public boolean getGlobalLandFlag(LandFlag flag) {
        return permissions.globalFlag(flag);
    }

    public FlagChangeResult setLandFlag(Player actor, Land land, LandFlag flag, Boolean value) {
        return permissions.setFlag(actor, land, flag, value);
    }

    public boolean canDelete(Player player, Land land) {
        return permissions.canDelete(player, land);
    }

    public String renameLand(Player actor, Land land, String newName) {
        return ownership.renameLand(actor, land, newName);
    }

    public String transferHighestOwner(Player actor, Land land, Player target) {
        return ownership.transferHighestOwner(actor, land, target);
    }

    public boolean deleteLand(Player actor, Land land) {
        return ownership.deleteLand(actor, land);
    }

    public boolean forceDeleteLand(Player actor, Land land) {
        return ownership.deleteLand(actor, land, true);
    }

    public boolean addMember(Player actor, Land land, String target) {
        return membership.addMember(actor, land, target);
    }

    public boolean removeMember(Player actor, Land land, String target) {
        return membership.removeMember(actor, land, target);
    }

    public boolean forceRemoveMember(Player actor, Land land, String target) {
        return membership.forceRemoveMember(actor, land, target);
    }

    public boolean setPermission(Player actor, Land land, String target,
                                 LandPermission permission, boolean allowed) {
        return membership.setPermission(actor, land, target, permission, allowed);
    }

    public boolean forceSetPermission(Player actor, Land land, String target,
                                      LandPermission permission,
                                      boolean allowed) {
        return membership.forceSetPermission(
                actor, land, target, permission, allowed);
    }

    public boolean setPermissionSet(Player actor, Land land, String target,
                                    Set<LandPermission> permissions) {
        return membership.setPermissionSet(actor, land, target, permissions);
    }

    public boolean forceSetPermissionSet(Player actor, Land land,
                                         String target,
                                         Set<LandPermission> permissions) {
        return membership.forceSetPermissionSet(
                actor, land, target, permissions);
    }

    public boolean hasPermissionInLand(String playerName, Land land, LandPermission permission,
                                       Set<String> visited) {
        return permissions.hasPermissionInLand(playerName, land, permission, visited);
    }

    public boolean canPlaceOrBreak(Player player, Block block, LandPermission permission) {
        return accessPolicy.checkBuild(player, block, permission).isAllowed();
    }

    public AccessCheck checkBuildPermission(Player player, Block block,
                                            LandPermission permission) {
        return accessPolicy.checkBuild(player, block, permission);
    }

    public AccessCheck checkLandPermission(Player player, Land land,
                                           LandPermission permission) {
        return accessPolicy.checkLand(player, land, permission);
    }

    public AccessCheck checkFees(Player player, Land land) {
        return accessPolicy.checkFees(player, land);
    }

    public boolean canChangeFamilyMembers(Land land) {
        return accessPolicy.canChangeFamilyMembers(land);
    }

    public boolean hasBypass(Player player) {
        return permissions.hasBypass(player);
    }

    public boolean isAdmin(Player player) {
        return permissions.isAdmin(player);
    }

    public boolean hasActionPermission(Player player, String permission) {
        return permissions.hasActionPermission(player, permission);
    }

    public boolean isOutsideUnlocked(Player player) {
        return permissions.isOutsideUnlocked(player);
    }

    public PlayerData getOrCreatePlayerData(Player player) {
        return sessions.getOrCreatePlayerData(player);
    }

    public PlayerData getOrCreatePlayerData(String playerName) {
        return sessions.getOrCreatePlayerData(playerName);
    }

    public PlayerData getPlayerData(String playerName) {
        return sessions.getPlayerData(playerName);
    }

    public void startSession(Player player) {
        sessions.startSession(player);
    }

    public void stopSession(Player player) {
        sessions.stopSession(player);
    }

    public void settleAllSessions() {
        sessions.settleAllSessions();
    }

    public boolean addMemberDirect(Land land, String playerName) {
        return membership.addMemberDirect(land, playerName);
    }

    public boolean forceAddMemberDirect(Land land, String playerName) {
        return membership.forceAddMemberDirect(land, playerName);
    }

    public Land getFamilyMain(Land land) {
        return membership.getFamilyMain(land);
    }

    public boolean isFamilyInDebt(Land land) {
        return membership.isFamilyInDebt(land);
    }

    public int getFamilyMemberCount(Land land) {
        return membership.getFamilyMemberCount(land);
    }

    public Set<String> getJoinedMembers(Land land) {
        return membership.getJoinedMembers(land);
    }

    public Set<String> getBuildAccessMembers(Land land) {
        return membership.getBuildAccessMembers(land);
    }

    public Set<String> getRentPayers(Land land) {
        return membership.getRentPayers(land);
    }

    public int getOccupiedChunks(Land land) {
        return index.getOccupiedChunks(land);
    }

    public List<String> getFamilyMemberNames(Land land) {
        return membership.getFamilyMemberNames(land);
    }

    public List<String> getFamilyMemberKeys(Land land) {
        return membership.getFamilyMemberKeys(land);
    }

    public boolean canAddFamilyMember(Land land, String playerName) {
        return membership.canAddFamilyMember(land, playerName);
    }

    public int getFamilyMemberLimit(Land land) {
        return membership.getFamilyMemberLimit(land);
    }

    public boolean leaveLand(Player player, Land land) {
        return membership.leaveLand(player, land);
    }

    public int getJoinedLandCount(String playerName) {
        return membership.getJoinedLandCount(playerName);
    }

    public int getJoinedLandLimit(String playerName) {
        return membership.getJoinedLandLimit(playerName);
    }

    public boolean canMeetJoinedLandLimit(Land land, String playerName) {
        return membership.canMeetJoinedLandLimit(land, playerName);
    }

    public Location getTeleportLocation(Land land) {
        if (land == null) {
            return null;
        }
        World world = Bukkit.getServer().getWorld(land.getWorld());
        if (world == null) {
            return null;
        }
        HomePoint home = land.getHome();
        if (home != null) {
            return new Location(world, home.getX(), home.getY(), home.getZ(),
                    home.getYaw(), home.getPitch());
        }
        Rect2i area = land.firstArea();
        if (area == null) {
            return null;
        }
        int x = (int) Math.floor(area.centerX());
        int z = (int) Math.floor(area.centerZ());
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (land.isSubclaim()) {
            y = Math.max(land.getMinY() + 1, Math.min(land.getMaxY(), y));
        }
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    public boolean setHome(Player actor, Land land, Location location) {
        return setHome(actor, land, location, true);
    }

    public boolean setHome(Player actor, Land land, Location location,
                           boolean requireInsideLand) {
        if (!canManage(actor, land) || location == null || location.getWorld() == null) {
            return false;
        }
        if (!land.getWorld().equalsIgnoreCase(
                location.getWorld().getName())) {
            return false;
        }
        if (requireInsideLand
                && !land.contains(location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(), location.getBlockZ())) {
            return false;
        }
        land.setHome(new HomePoint(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        return true;
    }

    public LandImportResult mergeImported(List<Land> imported) {
        return importMerger.merge(imported);
    }

}
