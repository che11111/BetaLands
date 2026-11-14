package dev.che1s.betalands.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LandInfo {
    private final String id;
    private final String name;
    private final String displayName;
    private final String owner;
    private final String world;
    private final String parentId;
    private final String parentName;
    private final boolean subclaim;
    private final boolean inheritsParentPermissions;
    private final long createdAt;
    private final String source;
    private final int minY;
    private final int maxY;
    private final long area;
    private final long volume;
    private final int occupiedChunks;
    private final LevelInfo level;
    private final double bankBalance;
    private final double maintenancePerCycle;
    private final boolean maintenanceExempt;
    private final long lastMaintenanceAt;
    private final long nextMaintenanceAt;
    private final boolean rentEnabled;
    private final double rentAmount;
    private final long lastRentAt;
    private final long nextRentAt;
    private final List<AreaInfo> areas;
    private final HomeInfo home;
    private final Map<LandFlag, Boolean> flags;
    private final Map<LandFlag, String> flagSources;
    private final List<MemberInfo> members;
    private final List<RentAccountInfo> rentAccounts;
    private final List<SubclaimInfo> subclaims;

    LandInfo(String id, String name, String displayName, String owner, String world,
             String parentId, String parentName, boolean subclaim,
             boolean inheritsParentPermissions, long createdAt, String source,
             int minY, int maxY, long area, long volume, int occupiedChunks,
             LevelInfo level, double bankBalance, double maintenancePerCycle,
             boolean maintenanceExempt, long lastMaintenanceAt,
             long nextMaintenanceAt, boolean rentEnabled,
             double rentAmount, long lastRentAt, long nextRentAt,
             List<AreaInfo> areas, HomeInfo home, Map<LandFlag, Boolean> flags,
             Map<LandFlag, String> flagSources, List<MemberInfo> members,
             List<RentAccountInfo> rentAccounts, List<SubclaimInfo> subclaims) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.owner = owner;
        this.world = world;
        this.parentId = parentId;
        this.parentName = parentName;
        this.subclaim = subclaim;
        this.inheritsParentPermissions = inheritsParentPermissions;
        this.createdAt = createdAt;
        this.source = source;
        this.minY = minY;
        this.maxY = maxY;
        this.area = area;
        this.volume = volume;
        this.occupiedChunks = occupiedChunks;
        this.level = level;
        this.bankBalance = bankBalance;
        this.maintenancePerCycle = maintenancePerCycle;
        this.maintenanceExempt = maintenanceExempt;
        this.lastMaintenanceAt = lastMaintenanceAt;
        this.nextMaintenanceAt = nextMaintenanceAt;
        this.rentEnabled = rentEnabled;
        this.rentAmount = rentAmount;
        this.lastRentAt = lastRentAt;
        this.nextRentAt = nextRentAt;
        this.areas = immutable(areas);
        this.home = home;
        this.flags = Collections.unmodifiableMap(new EnumMap<LandFlag, Boolean>(flags));
        this.flagSources = Collections.unmodifiableMap(
                new EnumMap<LandFlag, String>(flagSources));
        this.members = immutable(members);
        this.rentAccounts = immutable(rentAccounts);
        this.subclaims = immutable(subclaims);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getOwner() { return owner; }
    public String getWorld() { return world; }
    public String getParentId() { return parentId; }
    public String getParentName() { return parentName; }
    public boolean isSubclaim() { return subclaim; }
    public boolean inheritsParentPermissions() { return inheritsParentPermissions; }
    public long getCreatedAt() { return createdAt; }
    public String getSource() { return source; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public long getArea() { return area; }
    public long getVolume() { return volume; }
    public int getOccupiedChunks() { return occupiedChunks; }
    public LevelInfo getLevel() { return level; }
    public double getBankBalance() { return bankBalance; }
    public double getMaintenancePerCycle() { return maintenancePerCycle; }

    /**
     * @deprecated Maintenance is charged per configured cycle. This method now
     * returns the per-cycle amount and will be removed in a future API version.
     */
    @Deprecated
    public double getDailyMaintenance() { return maintenancePerCycle; }
    public boolean isMaintenanceExempt() { return maintenanceExempt; }
    public double getMaintenanceDebt() {
        return bankBalance < 0.0D ? -bankBalance : 0.0D;
    }
    public double getMaintenancePrepaidBalance() {
        return bankBalance > 0.0D ? bankBalance : 0.0D;
    }
    public long getLastMaintenanceAt() { return lastMaintenanceAt; }
    public long getNextMaintenanceAt() { return nextMaintenanceAt; }
    public boolean isRentEnabled() { return rentEnabled; }
    public double getRentAmount() { return rentAmount; }
    public double getNextExpansionPrice() {
        double raw = level.getClaimChunkPrice()
                + level.getClaimChunkIncrement() * occupiedChunks;
        return Math.round(raw * 100.0D) / 100.0D;
    }
    public long getLastRentAt() { return lastRentAt; }
    public long getNextRentAt() { return nextRentAt; }
    public List<AreaInfo> getAreas() { return areas; }
    public HomeInfo getHome() { return home; }
    public Map<LandFlag, Boolean> getFlags() { return flags; }
    public Map<LandFlag, String> getFlagSources() { return flagSources; }
    public List<MemberInfo> getMembers() { return members; }
    public List<RentAccountInfo> getRentAccounts() { return rentAccounts; }
    public List<SubclaimInfo> getSubclaims() { return subclaims; }

    public boolean isFlagEnabled(LandFlag flag) {
        return Boolean.TRUE.equals(flags.get(flag));
    }

    public static final class AreaInfo {
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        public AreaInfo(int minX, int minZ, int maxX, int maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public int getMinX() { return minX; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxZ() { return maxZ; }
    }

    public static final class HomeInfo {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        public HomeInfo(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }

    public static final class LevelInfo {
        private final String id;
        private final String displayName;
        private final int maxMainLands;
        private final int maxMainChunks;
        private final int maxSubclaimsPerMain;
        private final int maxFamilyMembers;
        private final int maxJoinedLands;
        private final double createMainPrice;
        private final double claimChunkPrice;
        private final double claimChunkIncrement;
        private final double subclaimPerBlockPrice;
        private final double maintenancePerChunkPerCycle;
        private final double maintenancePerMemberPerCycle;
        private final int maintenanceIntervalDays;

        public LevelInfo(String id, String displayName, int maxMainLands,
                         int maxMainChunks, int maxSubclaimsPerMain,
                         int maxFamilyMembers, int maxJoinedLands,
                         double createMainPrice, double claimChunkPrice,
                         double claimChunkIncrement, double subclaimPerBlockPrice,
                         double maintenancePerChunkPerDay,
                         double maintenancePerMemberPerDay) {
            this(id, displayName, maxMainLands, maxMainChunks,
                    maxSubclaimsPerMain, maxFamilyMembers, maxJoinedLands,
                    createMainPrice, claimChunkPrice, claimChunkIncrement,
                    subclaimPerBlockPrice, maintenancePerChunkPerDay,
                    maintenancePerMemberPerDay, 3);
        }

        public LevelInfo(String id, String displayName, int maxMainLands,
                         int maxMainChunks, int maxSubclaimsPerMain,
                         int maxFamilyMembers, int maxJoinedLands,
                         double createMainPrice, double claimChunkPrice,
                         double claimChunkIncrement, double subclaimPerBlockPrice,
                         double maintenancePerChunkPerDay,
                         double maintenancePerMemberPerDay,
                         int maintenanceIntervalDays) {
            this.id = id;
            this.displayName = displayName;
            this.maxMainLands = maxMainLands;
            this.maxMainChunks = maxMainChunks;
            this.maxSubclaimsPerMain = maxSubclaimsPerMain;
            this.maxFamilyMembers = maxFamilyMembers;
            this.maxJoinedLands = maxJoinedLands;
            this.createMainPrice = createMainPrice;
            this.claimChunkPrice = claimChunkPrice;
            this.claimChunkIncrement = claimChunkIncrement;
            this.subclaimPerBlockPrice = subclaimPerBlockPrice;
            this.maintenancePerChunkPerCycle = maintenancePerChunkPerDay;
            this.maintenancePerMemberPerCycle = maintenancePerMemberPerDay;
            this.maintenanceIntervalDays = Math.max(1, maintenanceIntervalDays);
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getMaxMainLands() { return maxMainLands; }
        public int getMaxMainChunks() { return maxMainChunks; }
        public int getMaxSubclaimsPerMain() { return maxSubclaimsPerMain; }
        public int getMaxFamilyMembers() { return maxFamilyMembers; }
        public int getMaxJoinedLands() { return maxJoinedLands; }
        public double getCreateMainPrice() { return createMainPrice; }
        public double getClaimChunkPrice() { return claimChunkPrice; }
        public double getClaimChunkIncrement() { return claimChunkIncrement; }
        public double getSubclaimPerBlockPrice() { return subclaimPerBlockPrice; }
        public double getMaintenancePerChunkPerCycle() {
            return maintenancePerChunkPerCycle;
        }
        public double getMaintenancePerMemberPerCycle() {
            return maintenancePerMemberPerCycle;
        }
        /** @deprecated Maintenance is charged per cycle. */
        @Deprecated
        public double getMaintenancePerChunkPerDay() {
            return maintenancePerChunkPerCycle;
        }
        /** @deprecated Maintenance is charged per cycle. */
        @Deprecated
        public double getMaintenancePerMemberPerDay() {
            return maintenancePerMemberPerCycle;
        }
        public int getMaintenanceIntervalDays() { return maintenanceIntervalDays; }
    }

    public static final class MemberInfo {
        private final String playerName;
        private final boolean directMember;
        private final boolean owner;
        private final Set<LandPermission> permissions;

        public MemberInfo(String playerName, boolean directMember, boolean owner,
                          Set<LandPermission> permissions) {
            this.playerName = playerName;
            this.directMember = directMember;
            this.owner = owner;
            this.permissions = permissions == null || permissions.isEmpty()
                    ? Collections.<LandPermission>emptySet()
                    : Collections.unmodifiableSet(permissions);
        }

        public String getPlayerName() { return playerName; }
        public boolean isDirectMember() { return directMember; }
        public boolean isOwner() { return owner; }
        public Set<LandPermission> getPermissions() { return permissions; }
    }

    public static final class RentAccountInfo {
        private final String playerName;
        private final double balance;

        public RentAccountInfo(String playerName, double balance) {
            this.playerName = playerName;
            this.balance = balance;
        }

        public String getPlayerName() { return playerName; }
        public double getBalance() { return balance; }
        public boolean isInDebt() { return balance < 0.0D; }
    }

    public static final class SubclaimInfo {
        private final String id;
        private final String name;
        private final String owner;

        public SubclaimInfo(String id, String name, String owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getOwner() { return owner; }
    }
}
