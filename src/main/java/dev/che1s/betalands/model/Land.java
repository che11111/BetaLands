package dev.che1s.betalands.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.util.Text;

public final class Land {
    private final String id;
    private String owner;
    private String name;
    private final String world;
    private final String parentId;
    private final List<Rect2i> areas;
    private final Map<String, EnumSet<LandPermission>> members;
    private final EnumMap<LandFlag, Boolean> flags;
    private final long createdAt;
    private int minY;
    private int maxY;
    private boolean inheritParentPermissions;
    private String source;
    private HomePoint home;
    private String parentDisplayName;
    private String levelId;
    private double bankBalance;
    private long lastMaintenanceAt;
    private boolean maintenanceExempt;
    private boolean rentEnabled;
    private double rentAmount;
    private long lastRentAt;
    private long membershipRevision;
    private long geometryRevision;
    private long deletedAt;
    private String deletedBy;

    public Land(String id, String owner, String name, String world, String parentId,
                List<Rect2i> areas, int minY, int maxY,
                boolean inheritParentPermissions, long createdAt, String source, HomePoint home) {
        this.id = Text.key(id);
        this.owner = owner;
        this.name = name;
        this.world = world;
        this.parentId = parentId == null ? null : Text.key(parentId);
        this.areas = new ArrayList<Rect2i>(areas);
        this.members = new LinkedHashMap<String, EnumSet<LandPermission>>();
        this.flags = new EnumMap<LandFlag, Boolean>(LandFlag.class);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.inheritParentPermissions = inheritParentPermissions;
        this.createdAt = createdAt;
        this.source = source == null ? "manual" : source;
        this.home = home;
        this.levelId = "default";
        this.lastMaintenanceAt = System.currentTimeMillis();
        this.rentAmount = 3.0D;
        this.lastRentAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public String getParentId() {
        return parentId;
    }

    public boolean isSubclaim() {
        return parentId != null;
    }

    public List<Rect2i> getAreas() {
        return Collections.unmodifiableList(areas);
    }

    public void replaceAreas(List<Rect2i> replacement) {
        areas.clear();
        areas.addAll(replacement);
        geometryRevision++;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setHeightRange(int minY, int maxY) {
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        geometryRevision++;
    }

    public boolean inheritsParentPermissions() {
        return inheritParentPermissions;
    }

    public void setInheritParentPermissions(boolean value) {
        this.inheritParentPermissions = value;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setOwner(String owner) {
        this.owner = owner;
        membershipRevision++;
    }

    public HomePoint getHome() {
        return home;
    }

    public void setHome(HomePoint home) {
        this.home = home;
    }

    public void setParentDisplayName(String parentDisplayName) {
        this.parentDisplayName = parentDisplayName;
    }

    public String getLevelId() {
        return levelId;
    }

    public void setLevelId(String levelId) {
        this.levelId = levelId == null || levelId.length() == 0
                ? "default" : Text.key(levelId);
    }

    public double getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(double bankBalance) {
        this.bankBalance = bankBalance;
    }

    public void addBankBalance(double amount) {
        this.bankBalance += amount;
    }

    public long getLastMaintenanceAt() {
        return lastMaintenanceAt;
    }

    public void setLastMaintenanceAt(long lastMaintenanceAt) {
        this.lastMaintenanceAt = lastMaintenanceAt <= 0L
                ? System.currentTimeMillis() : lastMaintenanceAt;
    }

    public boolean isMaintenanceExempt() {
        return maintenanceExempt;
    }

    public void setMaintenanceExempt(boolean maintenanceExempt) {
        this.maintenanceExempt = maintenanceExempt;
    }

    public boolean isRentEnabled() {
        return rentEnabled;
    }

    public void setRentEnabled(boolean rentEnabled) {
        this.rentEnabled = rentEnabled;
    }

    public double getRentAmount() {
        return rentAmount;
    }

    public void setRentAmount(double rentAmount) {
        this.rentAmount = Math.max(0.0D, rentAmount);
    }

    public long getLastRentAt() {
        return lastRentAt;
    }

    public void setLastRentAt(long lastRentAt) {
        this.lastRentAt = lastRentAt <= 0L ? System.currentTimeMillis() : lastRentAt;
    }

    public Map<String, EnumSet<LandPermission>> getMembers() {
        Map<String, EnumSet<LandPermission>> snapshot =
                new LinkedHashMap<String, EnumSet<LandPermission>>();
        for (Map.Entry<String, EnumSet<LandPermission>> entry
                : members.entrySet()) {
            snapshot.put(entry.getKey(), EnumSet.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public boolean hasMember(String playerName) {
        return members.containsKey(Text.key(playerName));
    }

    public EnumSet<LandPermission> getMemberPermissions(String playerName) {
        EnumSet<LandPermission> permissions =
                members.get(Text.key(playerName));
        return permissions == null ? null : EnumSet.copyOf(permissions);
    }

    public void setMemberPermissions(String playerName, Set<LandPermission> permissions) {
        if (permissions == null) {
            members.remove(Text.key(playerName));
        } else {
            members.put(Text.key(playerName), EnumSet.copyOf(permissions));
        }
        membershipRevision++;
    }

    public void setMemberPermission(String playerName, LandPermission permission, boolean allowed) {
        String key = Text.key(playerName);
        EnumSet<LandPermission> permissions = members.get(key);
        if (allowed) {
            if (permissions == null) {
                permissions = EnumSet.noneOf(LandPermission.class);
                members.put(key, permissions);
            }
            permissions.add(permission);
        } else if (permissions != null) {
            permissions.remove(permission);
        }
        membershipRevision++;
    }

    public void removeMember(String playerName) {
        members.remove(Text.key(playerName));
        membershipRevision++;
    }

    public long getMembershipRevision() {
        return membershipRevision;
    }

    public long getGeometryRevision() {
        return geometryRevision;
    }

    public boolean isDeleted() {
        return deletedAt > 0L;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void markDeleted(String actor, long deletedAt) {
        this.deletedAt = Math.max(1L, deletedAt);
        this.deletedBy = actor;
    }

    public String getStorageNameKey() {
        String current = Text.key(getTerritoryName());
        return isDeleted() ? "deleted:" + id + ":" + current : current;
    }

    public Boolean getFlagOverride(LandFlag flag) {
        return flags.get(flag);
    }

    public void setFlagOverride(LandFlag flag, Boolean value) {
        if (value == null) {
            flags.remove(flag);
        } else {
            flags.put(flag, value);
        }
    }

    public EnumMap<LandFlag, Boolean> getFlagOverrides() {
        return new EnumMap<LandFlag, Boolean>(flags);
    }

    public void setFlagOverrides(Map<LandFlag, Boolean> overrides) {
        flags.clear();
        if (overrides != null) {
            flags.putAll(overrides);
        }
    }

    public boolean isOwner(String playerName) {
        return owner.equalsIgnoreCase(playerName);
    }

    public boolean contains(String worldName, int x, int y, int z) {
        if (world == null || !world.equals(worldName)) {
            return false;
        }
        if (isSubclaim() && (y < minY || y > maxY)) {
            return false;
        }
        for (Rect2i area : areas) {
            if (area.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsXZ(String worldName, int x, int z) {
        if (world == null || !world.equals(worldName)) {
            return false;
        }
        for (Rect2i area : areas) {
            if (area.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    public long area() {
        long result = 0L;
        for (Rect2i area : areas) {
            result += area.area();
        }
        return result;
    }

    public long volume() {
        return isSubclaim() ? area() * (long) (maxY - minY + 1) : area();
    }

    public Rect2i firstArea() {
        return areas.isEmpty() ? null : areas.get(0);
    }

    public String getDisplayName() {
        return getTerritoryName();
    }

    public String getTerritoryName() {
        if (!isSubclaim()) {
            return name;
        }
        int separator = parentId.indexOf(':');
        String main = parentDisplayName != null
                ? parentDisplayName
                : (separator >= 0 ? parentId.substring(separator + 1) : parentId);
        return main + "." + name;
    }

    public Land copyForImport() {
        Land copy = new Land(id, owner, name, world, parentId, areas, minY, maxY,
                inheritParentPermissions, createdAt, source, home);
        for (Map.Entry<String, EnumSet<LandPermission>> entry : members.entrySet()) {
            copy.setMemberPermissions(entry.getKey(), entry.getValue());
        }
        copy.setFlagOverrides(flags);
        copy.setLevelId(levelId);
        copy.setBankBalance(bankBalance);
        copy.setLastMaintenanceAt(lastMaintenanceAt);
        copy.setMaintenanceExempt(maintenanceExempt);
        copy.setRentEnabled(rentEnabled);
        copy.setRentAmount(rentAmount);
        copy.setLastRentAt(lastRentAt);
        if (isDeleted()) {
            copy.markDeleted(deletedBy, deletedAt);
        }
        return copy;
    }
}
