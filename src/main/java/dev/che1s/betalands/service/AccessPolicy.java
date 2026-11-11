package dev.che1s.betalands.service;

import java.util.HashSet;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;

/**
 * Single policy entry point for land protection fees, bypasses and
 * permission checks. Membership mutations use the same debt gate.
 */
public final class AccessPolicy {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final PermissionService permissions;

    public AccessPolicy(BetaLandsPlugin plugin, LandManager lands,
                        PermissionService permissions) {
        this.plugin = plugin;
        this.lands = lands;
        this.permissions = permissions;
    }

    public AccessCheck checkBuild(Player player, Block block,
                                  LandPermission permission) {
        if (permissions.hasBypass(player)) {
            return AccessCheck.allowed(null);
        }
        Land land = lands.findLandAt(block);
        if (land != null) {
            return checkLand(player, land, permission);
        }
        if ((permission == LandPermission.BUILD
                || permission == LandPermission.BREAK)
                && !permissions.isOutsideUnlocked(player)) {
            return AccessCheck.outsideLocked(null);
        }
        return AccessCheck.allowed(null);
    }

    public AccessCheck checkLand(Player player, Land land,
                                 LandPermission permission) {
        AccessCheck fees = checkFees(player, land);
        if (!fees.isAllowed() || fees.getLand() == null) {
            return fees;
        }
        return permissions.hasPermissionInLand(player.getName(), land,
                permission, new HashSet<String>())
                ? AccessCheck.allowed(land) : AccessCheck.denied(land);
    }

    public AccessCheck checkFees(Player player, Land land) {
        if (player == null || land == null || permissions.hasBypass(player)) {
            return AccessCheck.allowed(land);
        }
        LandMaintenanceService maintenance = plugin == null
                ? null : plugin.getMaintenanceService();
        if (maintenance != null
                && maintenance.isRentInDebt(land, player.getName())) {
            return AccessCheck.rentDebt(land);
        }
        if (lands.isFamilyInDebt(land)) {
            return AccessCheck.maintenanceDebt(land);
        }
        return AccessCheck.allowed(land);
    }

    /**
     * Adding or removing family members is disabled while the family bank is
     * in debt. Permission changes that introduce BUILD access use this gate too.
     */
    public boolean canChangeFamilyMembers(Land land) {
        return land != null && !lands.isFamilyInDebt(land);
    }
}
