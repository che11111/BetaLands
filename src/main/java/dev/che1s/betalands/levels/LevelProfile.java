package dev.che1s.betalands.levels;

public final class LevelProfile {
    private final String id;
    private final String displayName;
    private final String permission;
    private final int priority;
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

    public LevelProfile(String id, String displayName, String permission, int priority,
                        int maxMainLands, int maxMainChunks, int maxSubclaimsPerMain,
                        int maxFamilyMembers,
                        int maxJoinedLands,
                        double createMainPrice, double claimChunkPrice,
                        double claimChunkIncrement,
                        double subclaimPerBlockPrice, double maintenancePerChunkPerDay,
                        double maintenancePerMemberPerDay) {
        this(id, displayName, permission, priority, maxMainLands, maxMainChunks,
                maxSubclaimsPerMain, maxFamilyMembers, maxJoinedLands,
                createMainPrice, claimChunkPrice, claimChunkIncrement,
                subclaimPerBlockPrice, maintenancePerChunkPerDay,
                maintenancePerMemberPerDay, 3);
    }

    public LevelProfile(String id, String displayName, String permission, int priority,
                        int maxMainLands, int maxMainChunks, int maxSubclaimsPerMain,
                        int maxFamilyMembers,
                        int maxJoinedLands,
                        double createMainPrice, double claimChunkPrice,
                        double claimChunkIncrement,
                        double subclaimPerBlockPrice, double maintenancePerChunkPerDay,
                        double maintenancePerMemberPerDay,
                        int maintenanceIntervalDays) {
        this.id = id;
        this.displayName = displayName;
        this.permission = permission;
        this.priority = priority;
        this.maxMainLands = Math.max(0, maxMainLands);
        this.maxMainChunks = Math.max(0, maxMainChunks);
        this.maxSubclaimsPerMain = Math.max(0, maxSubclaimsPerMain);
        this.maxFamilyMembers = Math.max(0, maxFamilyMembers);
        this.maxJoinedLands = Math.max(0, maxJoinedLands);
        this.createMainPrice = nonNegative(createMainPrice);
        this.claimChunkPrice = nonNegative(claimChunkPrice);
        this.claimChunkIncrement = nonNegative(claimChunkIncrement);
        this.subclaimPerBlockPrice = nonNegative(subclaimPerBlockPrice);
        this.maintenancePerChunkPerCycle = nonNegative(maintenancePerChunkPerDay);
        this.maintenancePerMemberPerCycle = nonNegative(maintenancePerMemberPerDay);
        this.maintenanceIntervalDays = Math.max(1, maintenanceIntervalDays);
    }

    private double nonNegative(double value) {
        return Math.max(0.0D, value);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPermission() {
        return permission;
    }

    public int getPriority() {
        return priority;
    }

    public int getMaxMainLands() {
        return maxMainLands;
    }

    public int getMaxMainChunks() {
        return maxMainChunks;
    }

    public int getMaxSubclaimsPerMain() {
        return maxSubclaimsPerMain;
    }

    public int getMaxFamilyMembers() {
        return maxFamilyMembers;
    }

    public int getMaxJoinedLands() {
        return maxJoinedLands;
    }

    public double getCreateMainPrice() {
        return createMainPrice;
    }

    public double getClaimChunkPrice() {
        return claimChunkPrice;
    }

    public double getClaimChunkIncrement() {
        return claimChunkIncrement;
    }

    public double getSubclaimPerBlockPrice() {
        return subclaimPerBlockPrice;
    }

    /**
     * Amount charged per chunk during one configured maintenance interval.
     */
    public double getMaintenancePerChunkPerCycle() {
        return maintenancePerChunkPerCycle;
    }

    /**
     * Amount charged per family member during one maintenance interval.
     */
    public double getMaintenancePerMemberPerCycle() {
        return maintenancePerMemberPerCycle;
    }

    /**
     * @deprecated Maintenance is charged per configured cycle, not per day.
     */
    @Deprecated
    public double getMaintenancePerChunkPerDay() {
        return maintenancePerChunkPerCycle;
    }

    /**
     * @deprecated Maintenance is charged per configured cycle, not per day.
     */
    @Deprecated
    public double getMaintenancePerMemberPerDay() {
        return maintenancePerMemberPerCycle;
    }

    public int getMaintenanceIntervalDays() {
        return maintenanceIntervalDays;
    }
}
