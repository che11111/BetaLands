package dev.che1s.betalands.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class PlayerLandAccess {
    private final String playerName;
    private final LandInfo land;
    private final boolean owner;
    private final boolean rentPayer;
    private final Set<LandPermission> permissions;
    private final double dailyRent;
    private final double rentBalance;
    private final double familyDailyMaintenance;
    private final double maintenancePrepaidBalance;
    private final long nextRentAt;
    private final long nextMaintenanceAt;

    PlayerLandAccess(String playerName, LandInfo land, boolean owner, boolean rentPayer,
                     Set<LandPermission> permissions, double dailyRent,
                     double rentBalance, double familyDailyMaintenance,
                     double maintenancePrepaidBalance,
                     long nextRentAt, long nextMaintenanceAt) {
        this.playerName = playerName;
        this.land = land;
        this.owner = owner;
        this.rentPayer = rentPayer;
        this.permissions = permissions == null || permissions.isEmpty()
                ? Collections.<LandPermission>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
        this.dailyRent = dailyRent;
        this.rentBalance = rentBalance;
        this.familyDailyMaintenance = familyDailyMaintenance;
        this.maintenancePrepaidBalance = maintenancePrepaidBalance;
        this.nextRentAt = nextRentAt;
        this.nextMaintenanceAt = nextMaintenanceAt;
    }

    public String getPlayerName() { return playerName; }
    public LandInfo getLand() { return land; }
    public boolean isOwner() { return owner; }
    public boolean isRentPayer() { return rentPayer; }
    public Set<LandPermission> getPermissions() { return permissions; }
    public boolean hasPermission(LandPermission permission) {
        return permission != null && permissions.contains(permission);
    }
    public double getDailyRent() { return dailyRent; }
    public double getRentBalance() { return rentBalance; }
    public boolean isRentInDebt() { return rentBalance < 0.0D; }
    public double getFamilyDailyMaintenance() { return familyDailyMaintenance; }
    public double getMaintenancePrepaidBalance() { return maintenancePrepaidBalance; }
    public long getNextRentAt() { return nextRentAt; }
    public long getNextMaintenanceAt() { return nextMaintenanceAt; }
}
