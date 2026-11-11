package dev.che1s.betalands.service;

import dev.che1s.betalands.model.Land;

/**
 * Result of a protection or fee-policy evaluation.
 */
public final class AccessCheck {
    private final boolean allowed;
    private final boolean outsideLocked;
    private final boolean rentDebt;
    private final boolean maintenanceDebt;
    private final Land land;

    private AccessCheck(boolean allowed, boolean outsideLocked, boolean rentDebt,
                        boolean maintenanceDebt, Land land) {
        this.allowed = allowed;
        this.outsideLocked = outsideLocked;
        this.rentDebt = rentDebt;
        this.maintenanceDebt = maintenanceDebt;
        this.land = land;
    }

    public static AccessCheck allowed(Land land) {
        return new AccessCheck(true, false, false, false, land);
    }

    public static AccessCheck denied(Land land) {
        return new AccessCheck(false, false, false, false, land);
    }

    public static AccessCheck outsideLocked(Land land) {
        return new AccessCheck(false, true, false, false, land);
    }

    public static AccessCheck rentDebt(Land land) {
        return new AccessCheck(false, false, true, false, land);
    }

    public static AccessCheck maintenanceDebt(Land land) {
        return new AccessCheck(false, false, false, true, land);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isOutsideLocked() {
        return outsideLocked;
    }

    public boolean isRentDebt() {
        return rentDebt;
    }

    public boolean isMaintenanceDebt() {
        return maintenanceDebt;
    }

    public Land getLand() {
        return land;
    }
}
