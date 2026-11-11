package dev.che1s.betalands.service;

import java.util.HashSet;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.permission.PermissionNodes;

/**
 * Evaluates land permissions, administrative bypass and protection flags.
 */
public final class PermissionService {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;

    public PermissionService(BetaLandsPlugin plugin, LandManager lands) {
        this.plugin = plugin;
        this.lands = lands;
    }

    public boolean canManage(Player player, Land land) {
        if (land == null) {
            return false;
        }
        if (isAdmin(player) || land.isOwner(player.getName())) {
            return true;
        }
        return hasPermissionInLand(player.getName(), land,
                LandPermission.MANAGE, new HashSet<String>());
    }

    public boolean canEditLandFlags(Player player, Land land) {
        if (land == null) {
            return false;
        }
        if (isAdmin(player) || canManage(player, land)) {
            return true;
        }
        if (land.isSubclaim()) {
            Land parent = lands.getById(land.getParentId());
            return parent != null && canManage(player, parent);
        }
        return false;
    }

    public boolean effectiveFlag(Land land, LandFlag flag) {
        if (land != null) {
            Boolean override = land.getFlagOverride(flag);
            if (override != null) {
                return override.booleanValue();
            }
            if (land.getParentId() != null) {
                Land parent = lands.getById(land.getParentId());
                if (parent != null) {
                    return effectiveFlag(parent, flag);
                }
            }
        }
        return globalFlag(flag);
    }

    public String flagSource(Land land, LandFlag flag) {
        Land current = land;
        while (current != null) {
            if (current.getFlagOverride(flag) != null) {
                return current.getDisplayName();
            }
            current = current.getParentId() == null
                    ? null : lands.getById(current.getParentId());
        }
        return "global default";
    }

    public boolean globalFlag(LandFlag flag) {
        switch (flag) {
            case EXPLOSION:
                return plugin.getSettings().areExplosionsAllowedInLands();
            case FIRE:
                return plugin.getSettings().isFireAllowedInLands();
            case FLUID:
                return !plugin.getSettings().shouldBlockFluidCrossing();
            case PVP:
                return plugin.getSettings().isPvpAllowedByDefault();
            case TP:
                return plugin.getSettings().isPublicTeleportAllowedByDefault();
            case MOBS:
                return plugin.getSettings().isMobSpawningAllowedByDefault();
            case TRAMPLING:
                return plugin.getSettings().isTramplingAllowedByDefault();
            default:
                return false;
        }
    }

    public FlagChangeResult setFlag(Player actor, Land land,
                                                LandFlag flag, Boolean value) {
        if (land == null) {
            return FlagChangeResult.error("Land not found.");
        }
        if (!isAdmin(actor) && !plugin.getSettings().isLandFlagEditingAllowed()) {
            return FlagChangeResult.error(
                    "Land owners may not edit land flags on this server.");
        }
        if (!canEditLandFlags(actor, land)) {
            return FlagChangeResult.error("You cannot manage this land.");
        }
        land.setFlagOverride(flag, value);
        return FlagChangeResult.success(land, flag,
                effectiveFlag(land, flag), flagSource(land, flag));
    }

    public boolean canDelete(Player player, Land land) {
        return land != null && (isAdmin(player) || land.isOwner(player.getName()));
    }

    public boolean hasPermissionInLand(String playerName, Land land,
                                       LandPermission permission, java.util.Set<String> visited) {
        if (land == null) {
            return false;
        }
        if (land.isOwner(playerName)) {
            return true;
        }
        if (!visited.add(land.getId())) {
            return false;
        }
        java.util.EnumSet<LandPermission> explicit =
                land.getMemberPermissions(playerName);
        if (explicit != null) {
            return explicit.contains(permission);
        }
        if (land.inheritsParentPermissions() && land.getParentId() != null) {
            return hasPermissionInLand(playerName, lands.getById(land.getParentId()),
                    permission, visited);
        }
        return false;
    }

    public boolean hasBypass(Player player) {
        if (player == null) {
            return false;
        }
        if (player.isOp() && plugin.getSettings().isOpBypass()) {
            return true;
        }
        return !(player.isOp() && !plugin.getSettings().isOpBypass())
                && player.hasPermission(PermissionNodes.BYPASS);
    }

    public boolean isAdmin(Player player) {
        return player != null
                && (player.isOp() || player.hasPermission(PermissionNodes.ADMIN));
    }

    public boolean hasActionPermission(Player player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        if (isAdmin(player)) {
            return true;
        }
        return player.hasPermission(permission);
    }

    public boolean isOutsideUnlocked(Player player) {
        long requiredHours = plugin.getSettings().getOutsideBuildHours();
        if (requiredHours <= 0L) {
            return true;
        }
        return lands.getOrCreatePlayerData(player)
                .getTotalSeconds(System.currentTimeMillis()) >= requiredHours * 3600L;
    }
}
