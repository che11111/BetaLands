package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.permission.PermissionNodes;

final class TeleportCommandHandler extends CommandSupport {
    TeleportCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "setspawn".equals(subcommand) || "spawn".equals(subcommand)
                || "tp".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args) {
        if ("setspawn".equals(subcommand)) {
            return setSpawn(sender, args);
        }
        if ("spawn".equals(subcommand) || "tp".equals(subcommand)) {
            return teleport(sender, args, subcommand);
        }
        return false;
    }

    private boolean setSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_SETSPAWN)) {
            noPermission(sender);
            return true;
        }
        Land land = args.length >= 2 ? resolve(sender, args[1])
                : resolveManageableContextLand(player);
        if (land == null) {
            sendContextLandError(sender, args.length >= 2 ? args[1] : null);
            return true;
        }
        if (!plugin.getLandManager().setHome(
                player, land, player.getLocation())) {
            msg(sender, "spawn.set-failed",
                    "&cSet spawn failed: you need manage permission and must stand inside the land.");
            return true;
        }
        msg(sender, "spawn.set", "&aSet the spawn for {land}.",
                "land", land.getDisplayName());
        auditLand(sender, "SET_SPAWN", null, land, "Set land spawn");
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args,
                             String subcommand) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.TELEPORT)) {
            noPermission(sender);
            return true;
        }
        Land land;
        if (args.length >= 2) {
            land = resolve(sender, args[1]);
        } else {
            List<Land> candidates = teleportLandCandidates(
                    player.getName(),
                    plugin.getLandManager().getOwnedMainLands(player.getName()),
                    plugin.getLandManager().getAccessibleLands(player.getName()));
            if (candidates.size() > 1) {
                msg(sender, "spawn.land-required",
                        "&cYou have access to multiple lands. Specify a land name: /land {command} <land>.",
                        "command", subcommand);
                return true;
            }
            land = candidates.isEmpty() ? null : candidates.get(0);
        }
        if (land == null) {
            msg(sender, "spawn.not-found",
                    "&cNo teleportable land found.");
            return true;
        }
        boolean owner = land.isOwner(player.getName());
        boolean member = land.hasMember(player.getName());
        boolean admin = plugin.getLandManager().isAdmin(player);
        boolean publicTeleport = plugin.getLandManager()
                .getEffectiveLandFlag(land, LandFlag.TP);
        if (!canAccessTeleport(owner, member, admin, publicTeleport)) {
            msg(sender, "spawn.no-access",
                    "&cPublic teleport is disabled and you are not a member of this land.");
            return true;
        }
        boolean memberTeleport = appliesFeeRestrictions(owner, member, admin);
        if (memberTeleport && plugin.getLandManager().isFamilyInDebt(land)) {
            msg(sender, "spawn.debt",
                    "&cTeleporting to this land is disabled while its family bank is in debt.");
            return true;
        }
        if (memberTeleport && plugin.getMaintenanceService().isRentInDebt(
                land, player.getName())) {
            msg(sender, "spawn.rent-debt",
                    "&cYou owe rent for this land and cannot teleport there.");
            return true;
        }
        if (memberTeleport
                && plugin.getMaintenanceService().hasMaintenanceDebt(land)) {
            msg(sender, "spawn.maintenance-debt",
                    "&cThis land has unpaid maintenance fees and cannot be used.");
            return true;
        }
        Location target = plugin.getLandManager().getTeleportLocation(land);
        if (target == null) {
            msg(sender, "spawn.world-unavailable",
                    "&cThe target world is unavailable.");
            return true;
        }
        player.teleport(target);
        msg(sender, "spawn.teleport", "&aTeleported to {land}.",
                "land", land.getDisplayName());
        return true;
    }

    static boolean canAccessTeleport(boolean owner, boolean member,
                                     boolean admin, boolean publicTeleport) {
        return owner || member || admin || publicTeleport;
    }

    static boolean appliesFeeRestrictions(boolean owner, boolean member,
                                          boolean admin) {
        return !admin && (owner || member);
    }

    static List<Land> teleportLandCandidates(String playerName,
                                             List<Land> ownedMainLands,
                                             List<Land> accessibleLands) {
        List<Land> candidates = new ArrayList<Land>();
        if (ownedMainLands != null) {
            for (Land land : ownedMainLands) {
                if (land != null && !candidates.contains(land)) {
                    candidates.add(land);
                }
            }
        }
        if (accessibleLands != null) {
            for (Land land : accessibleLands) {
                if (land != null && !land.isOwner(playerName)
                        && !candidates.contains(land)) {
                    candidates.add(land);
                }
            }
        }
        return candidates;
    }
}
