package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.util.Text;

/**
 * Owns family membership, effective member sets and joined-land quotas.
 */
public final class MembershipService {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final PermissionService permissions;
    private final AccessPolicy accessPolicy;
    private final Map<String, Integer> familyMemberCache =
            new HashMap<String, Integer>();
    private final Map<String, Set<String>> buildAccessCache =
            new HashMap<String, Set<String>>();

    public MembershipService(BetaLandsPlugin plugin, LandManager lands,
                             PermissionService permissions,
                             AccessPolicy accessPolicy) {
        this.plugin = plugin;
        this.lands = lands;
        this.permissions = permissions;
        this.accessPolicy = accessPolicy;
    }

    public boolean addMember(Player actor, Land land, String target) {
        if (!permissions.canManage(actor, land) || target == null || target.length() == 0) {
            return false;
        }
        if (!accessPolicy.canChangeFamilyMembers(land)
                || !canAddFamilyMember(land, target)) {
            return false;
        }
        if (land.isOwner(target) || !canMeetJoinedLandLimit(land, target)) {
            return false;
        }
        grantDefaultPermissions(land, target);
        return true;
    }

    public boolean addMemberDirect(Land land, String playerName) {
        if (land == null || land.isOwner(playerName)) {
            return true;
        }
        if (!accessPolicy.canChangeFamilyMembers(land)
                || !canAddFamilyMember(land, playerName)
                || !canMeetJoinedLandLimit(land, playerName)) {
            return false;
        }
        grantDefaultPermissions(land, playerName);
        return true;
    }

    public boolean forceAddMemberDirect(Land land, String playerName) {
        if (land == null || playerName == null || playerName.length() == 0) {
            return false;
        }
        if (land.isOwner(playerName)) {
            return true;
        }
        grantDefaultPermissions(land, playerName);
        return true;
    }

    public boolean removeMember(Player actor, Land land, String target) {
        if (!permissions.canManage(actor, land)
                || land.isOwner(target)
                || !accessPolicy.canChangeFamilyMembers(land)) {
            return false;
        }
        return loseAllLandPermissions(land, target);
    }

    public boolean forceRemoveMember(Player actor, Land land, String target) {
        if (!permissions.canManage(actor, land) || land.isOwner(target)) {
            return false;
        }
        return loseAllLandPermissions(land, target);
    }

    public boolean setPermission(Player actor, Land land, String target,
                                 LandPermission permission, boolean allowed) {
        if (!permissions.canManage(actor, land) || land.isOwner(target)) {
            return false;
        }
        if (allowed && permission == LandPermission.BUILD
                && !canGrantBuildAccess(land, target)) {
            return false;
        }
        if (!land.hasMember(target)) {
            land.setMemberPermissions(target, EnumSet.noneOf(LandPermission.class));
        }
        land.setMemberPermission(target, permission, allowed);
        lands.refreshMemberIndex(land);
        return true;
    }

    public boolean setPermissionSet(Player actor, Land land, String target,
                                    Set<LandPermission> requested) {
        if (!permissions.canManage(actor, land) || land.isOwner(target)) {
            return false;
        }
        EnumSet<LandPermission> values = requested == null
                ? EnumSet.noneOf(LandPermission.class) : EnumSet.copyOf(requested);
        if (values.contains(LandPermission.BUILD)
                && !canGrantBuildAccess(land, target)) {
            return false;
        }
        land.setMemberPermissions(target, values);
        lands.refreshMemberIndex(land);
        return true;
    }

    public boolean forceSetPermission(Player actor, Land land, String target,
                                      LandPermission permission,
                                      boolean allowed) {
        if (!permissions.canManage(actor, land) || land.isOwner(target)) {
            return false;
        }
        if (!land.hasMember(target)) {
            land.setMemberPermissions(target,
                    EnumSet.noneOf(LandPermission.class));
        }
        land.setMemberPermission(target, permission, allowed);
        lands.refreshMemberIndex(land);
        return true;
    }

    public boolean forceSetPermissionSet(Player actor, Land land,
                                         String target,
                                         Set<LandPermission> requested) {
        if (!permissions.canManage(actor, land) || land.isOwner(target)) {
            return false;
        }
        land.setMemberPermissions(target, requested == null
                ? EnumSet.noneOf(LandPermission.class)
                : requested);
        lands.refreshMemberIndex(land);
        return true;
    }

    boolean canGrantBuildAccess(Land land, String target) {
        if (permissions.hasPermissionInLand(target, land,
                LandPermission.BUILD, new HashSet<String>())) {
            return true;
        }
        return accessPolicy.canChangeFamilyMembers(land)
                && canAddFamilyMember(land, target)
                && canMeetJoinedLandLimit(land, target);
    }

    public boolean leaveLand(Player player, Land land) {
        if (land == null || land.isOwner(player.getName())) {
            return false;
        }
        return loseAllLandPermissions(land, player.getName());
    }

    public Land getFamilyMain(Land land) {
        if (land == null) {
            return null;
        }
        return land.isSubclaim() ? lands.getById(land.getParentId()) : land;
    }

    public boolean isFamilyInDebt(Land land) {
        Land main = getFamilyMain(land);
        return main != null && !main.isMaintenanceExempt()
                && main.getBankBalance() < 0.0D;
    }

    public int getFamilyMemberCount(Land land) {
        Land main = getFamilyMain(land);
        if (main == null) {
            return 0;
        }
        String cacheKey = familyMemberCacheKey(main);
        Integer cached = familyMemberCache.get(cacheKey);
        if (cached != null) {
            return cached.intValue();
        }
        Set<String> candidates = collectFamilyMembers(main);
        Set<String> joined = new HashSet<String>();
        for (String playerName : candidates) {
            if (!playerName.equals(Text.key(main.getOwner()))
                    && permissions.hasPermissionInLand(playerName, main,
                    LandPermission.BUILD, new HashSet<String>())) {
                joined.add(playerName);
            }
        }
        for (Land child : lands.getChildren(main.getId())) {
            for (String playerName : candidates) {
                if (!playerName.equals(Text.key(main.getOwner()))
                        && permissions.hasPermissionInLand(playerName, child,
                        LandPermission.BUILD, new HashSet<String>())) {
                    joined.add(playerName);
                }
            }
        }
        remember(familyMemberCache, cacheKey, Integer.valueOf(joined.size()));
        return joined.size();
    }

    public Set<String> getJoinedMembers(Land land) {
        Land main = getFamilyMain(land);
        Set<String> joined = new HashSet<String>();
        if (main == null || land == null) {
            return joined;
        }
        for (String playerName : collectFamilyMembers(main)) {
            EnumSet<LandPermission> explicit =
                    land.getMemberPermissions(playerName);
            if (!land.isOwner(playerName) && explicit != null
                    && explicit.contains(LandPermission.BUILD)) {
                joined.add(playerName);
            }
        }
        return joined;
    }

    public Set<String> getBuildAccessMembers(Land land) {
        Land main = getFamilyMain(land);
        Set<String> members = new LinkedHashSet<String>();
        if (main == null || land == null) {
            return members;
        }
        String cacheKey = accessCacheKey(land, main);
        Set<String> cached = buildAccessCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (String playerName : collectFamilyMembers(main)) {
            if (!land.isOwner(playerName) && permissions.hasPermissionInLand(
                    playerName, land, LandPermission.BUILD, new HashSet<String>())) {
                members.add(playerName);
            }
        }
        Set<String> result = Collections.unmodifiableSet(members);
        remember(buildAccessCache, cacheKey, result);
        return result;
    }

    public Set<String> getRentPayers(Land land) {
        Land main = getFamilyMain(land);
        Set<String> payers = new LinkedHashSet<String>();
        if (main == null || land == null) {
            return payers;
        }
        for (String playerName : collectFamilyMembers(main)) {
            if (!land.isOwner(playerName) && permissions.hasPermissionInLand(
                    playerName, land, LandPermission.RENT,
                    new HashSet<String>())) {
                payers.add(playerName);
            }
        }
        return Collections.unmodifiableSet(payers);
    }

    public List<String> getFamilyMemberNames(Land land) {
        List<String> result = new ArrayList<String>();
        for (String key : getFamilyMemberKeys(land)) {
            PlayerData data = lands.getPlayerData(key);
            result.add(data == null || data.getLastName() == null
                    || data.getLastName().length() == 0 ? key : data.getLastName());
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public List<String> getFamilyMemberKeys(Land land) {
        Land main = getFamilyMain(land);
        List<String> result = new ArrayList<String>();
        if (main == null) {
            return result;
        }
        Set<String> keys = collectFamilyMembers(main);
        keys.remove(Text.key(main.getOwner()));
        result.addAll(keys);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public boolean canAddFamilyMember(Land land, String playerName) {
        Land main = getFamilyMain(land);
        if (main == null) {
            return false;
        }
        String key = Text.key(playerName);
        if (key.equals(Text.key(main.getOwner())) || familyContainsPlayer(main, key)) {
            return true;
        }
        return getFamilyMemberCount(main)
                < plugin.getLevels().getProfile(main).getMaxFamilyMembers();
    }

    public int getFamilyMemberLimit(Land land) {
        Land main = getFamilyMain(land);
        return main == null ? 0
                : plugin.getLevels().getProfile(main).getMaxFamilyMembers();
    }

    public int getJoinedLandCount(String playerName) {
        int count = 0;
        for (Land land : lands.getMemberLands(playerName)) {
            EnumSet<LandPermission> explicit =
                    land.getMemberPermissions(playerName);
            if (!land.isOwner(playerName) && explicit != null
                    && explicit.contains(LandPermission.BUILD)) {
                count++;
            }
        }
        return count;
    }

    public int getJoinedLandLimit(String playerName) {
        return plugin.getLevels().getProfileForName(playerName).getMaxJoinedLands();
    }

    public boolean canMeetJoinedLandLimit(Land land, String playerName) {
        EnumSet<LandPermission> previous = land.getMemberPermissions(playerName);
        if (previous != null && previous.contains(LandPermission.BUILD)) {
            return true;
        }
        return getJoinedLandCount(playerName) < getJoinedLandLimit(playerName);
    }

    private Set<String> collectFamilyMembers(Land main) {
        Set<String> unique = new HashSet<String>();
        unique.addAll(main.getMembers().keySet());
        for (Land child : lands.getChildren(main.getId())) {
            unique.add(Text.key(child.getOwner()));
            unique.addAll(child.getMembers().keySet());
        }
        return unique;
    }

    private boolean familyContainsPlayer(Land main, String key) {
        if (permissions.hasPermissionInLand(key, main,
                LandPermission.BUILD, new HashSet<String>())) {
            return true;
        }
        for (Land child : lands.getChildren(main.getId())) {
            if (permissions.hasPermissionInLand(key, child,
                    LandPermission.BUILD, new HashSet<String>())) {
                return true;
            }
        }
        return false;
    }

    private void grantDefaultPermissions(Land land, String playerName) {
        EnumSet<LandPermission> values = land.getMemberPermissions(playerName);
        values = values == null ? EnumSet.noneOf(LandPermission.class)
                : EnumSet.copyOf(values);
        boolean pvp = plugin != null && plugin.getSettings() != null
                && plugin.getSettings().isPvpAllowedByDefault();
        values.addAll(LandPermission.defaultsForMember(pvp));
        land.setMemberPermissions(playerName, values);
        lands.refreshMemberIndex(land);
    }

    private boolean loseAllLandPermissions(Land land, String playerName) {
        boolean hadAccess = permissions.hasPermissionInLand(playerName, land,
                LandPermission.BUILD, new HashSet<String>())
                || permissions.hasPermissionInLand(playerName, land,
                LandPermission.USE, new HashSet<String>())
                || land.hasMember(playerName);
        Land parent = land.isSubclaim() ? lands.getById(land.getParentId()) : null;
        boolean inherited = parent != null
                && hasAnyPermission(playerName, parent, new HashSet<String>());
        if (inherited) {
            land.setMemberPermissions(playerName,
                    EnumSet.noneOf(LandPermission.class));
        } else {
            land.removeMember(playerName);
        }
        lands.refreshMemberIndex(land);
        return hadAccess;
    }

    private boolean hasAnyPermission(String playerName, Land land, Set<String> visited) {
        if (land == null || !visited.add(land.getId())) {
            return false;
        }
        if (land.isOwner(playerName)) {
            return true;
        }
        EnumSet<LandPermission> explicit = land.getMemberPermissions(playerName);
        if (explicit != null) {
            return !explicit.isEmpty();
        }
        return land.inheritsParentPermissions() && land.getParentId() != null
                && hasAnyPermission(playerName, lands.getById(land.getParentId()), visited);
    }

    private String accessCacheKey(Land land, Land main) {
        return land.getId() + ":" + land.getMembershipRevision()
                + ":" + main.getMembershipRevision();
    }

    private String familyMemberCacheKey(Land main) {
        long revision = main.getMembershipRevision();
        for (Land child : lands.getChildren(main.getId())) {
            revision = revision * 31L + child.getMembershipRevision();
        }
        return main.getId() + ":" + revision;
    }

    private <T> void remember(Map<String, T> cache, String key, T value) {
        if (cache.size() >= 512) {
            cache.clear();
        }
        cache.put(key, value);
    }
}
