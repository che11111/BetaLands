package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.Text;

/**
 * Administrative namespace that executes the regular land command set with
 * administrator privileges. The force variant additionally bypasses member
 * quota checks for member additions.
 */
final class AdminLandCommandHandler extends CommandSupport {
    private final PlayerCommandHandler playerCommands;
    private final FinanceCommandHandler financeCommands;

    AdminLandCommandHandler(BetaLandsPlugin plugin,
                            PlayerCommandHandler playerCommands,
                            FinanceCommandHandler financeCommands) {
        super(plugin);
        this.playerCommands = playerCommands;
        this.financeCommands = financeCommands;
    }

    boolean handle(CommandSender sender, String[] args, boolean force) {
        if (!sender.isOp() && !sender.hasPermission(PermissionNodes.ADMIN)) {
            noPermission(sender);
            return true;
        }
        if (force && !sender.isOp()
                && !sender.hasPermission(PermissionNodes.ADMIN_FORCE)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            showUsage(sender, force);
            return true;
        }
        String subcommand = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("add".equals(subcommand)) {
            return addMember(sender, args, force);
        }
        if ("bank".equals(subcommand)) {
            return financeCommands.bank(sender, tail(args, "bank"), force);
        }
        if ("bank-all".equals(subcommand)) {
            return financeCommands.bankAll(sender, tail(args, "bank-all"));
        }
        if ("rent".equals(subcommand)) {
            return financeCommands.rent(sender, tail(args, "rent"));
        }
        if (force && "delete".equals(subcommand)) {
            return forceDelete(sender, args);
        }
        if (force && "setspawn".equals(subcommand)) {
            return forceSetSpawn(sender, args);
        }
        if (force && ("spawn".equals(subcommand) || "tp".equals(subcommand))) {
            return forceTeleport(sender, args);
        }
        if ("accept".equals(subcommand) || "deny".equals(subcommand)
                || "leave".equals(subcommand)) {
            msg(sender, "admin-land.personal-only",
                    "&cUse /land {command} as the affected player; administrators cannot act on another player's membership acceptance or departure.",
                    "command", subcommand);
            return true;
        }
        String[] playerArgs = tail(args, subcommand);
        if (playerCommands.handle(subcommand, sender, playerArgs, force)) {
            return true;
        }
        msg(sender, "admin-land.unknown",
                "&cUnknown admin-land command. Use /land admin-land for usage.");
        return true;
    }

    private boolean addMember(CommandSender sender, String[] args,
                              boolean force) {
        if (args.length != 4) {
            msg(sender, force
                            ? "admin-land.force-add-usage"
                            : "admin-land.add-usage",
                    force
                            ? "&eUsage: /land admin-land-force add <player> <land>"
                            : "&eUsage: /land admin-land add <player> <land>");
            return true;
        }
        String playerName = args[2];
        if (!Text.validName(playerName, 2, 16)) {
            msg(sender, "common.invalid-player-name",
                    "&cPlayer names may only contain letters, numbers, underscores, and hyphens.");
            return true;
        }
        Land land = resolve(sender, args[3]);
        if (land == null) {
            sendContextLandError(sender, args[3]);
            return true;
        }
        if (!force
                && !plugin.getLandManager().canChangeFamilyMembers(land)) {
            msg(sender, "member.debt",
                    "&cMember changes are disabled while the land family bank is in debt.");
            return true;
        }
        boolean added;
        if (force) {
            added = plugin.getLandManager()
                    .forceAddMemberDirect(land, playerName);
        } else {
            if (!plugin.getLandManager()
                    .canAddFamilyMember(land, playerName)) {
                msg(sender, "member.family-full",
                        "&cThis land family has reached its member limit ({limit}).",
                        "limit", Integer.toString(
                                plugin.getLandManager()
                                        .getFamilyMemberLimit(land)));
                return true;
            }
            if (!plugin.getLandManager()
                    .canMeetJoinedLandLimit(land, playerName)) {
                LevelProfile profile =
                        plugin.getLevels().getProfileForName(playerName);
                msg(sender, "member.joined-limit",
                        "&c{player} has reached the joined-land limit for their level ({limit}).",
                        "player", playerName,
                        "limit", Integer.toString(
                                profile.getMaxJoinedLands()));
                return true;
            }
            added = plugin.getLandManager()
                    .addMemberDirect(land, playerName);
        }
        if (!added) {
            msg(sender, "admin-land.add-failed",
                    "&cCould not add {player} to {land}.",
                    "player", playerName, "land", land.getDisplayName());
            return true;
        }
        msg(sender, force
                        ? "admin-land.force-add-success"
                        : "admin-land.add-success",
                force
                        ? "&aForce-added {player} to {land}; member and joined-land quotas were bypassed."
                        : "&aAdded {player} to {land}.",
                "player", playerName, "land", land.getDisplayName());
        auditLand(sender,
                force ? "ADMIN_LAND_FORCE_ADD_MEMBER"
                        : "ADMIN_LAND_ADD_MEMBER",
                playerName, land,
                force
                        ? "Administrator force-added member; quotas bypassed"
                        : "Administrator added member; quotas enforced");
        refreshMaps();
        return true;
    }

    private boolean forceDelete(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        int landIndex = args.length == 3 ? 2
                : (args.length == 4 && "confirm".equalsIgnoreCase(args[2])
                ? 3 : -1);
        if (landIndex < 0) {
            msg(sender, "admin-land.force-delete-usage",
                    "&eUsage: /land admin-land-force delete <land>");
            return true;
        }
        Land land = resolve(sender, args[landIndex]);
        if (land == null) {
            sendContextLandError(sender, args[landIndex]);
            return true;
        }
        Land bankLand = plugin.getMaintenanceService().getBankLand(land);
        double previousBalance = bankLand == null
                ? 0.0D : bankLand.getBankBalance();
        if (!plugin.getLandManager().forceDeleteLand(actor, land)) {
            msg(sender, "admin-land.force-delete-failed",
                    "&cCould not force-delete {land}.",
                    "land", land.getDisplayName());
            return true;
        }
        auditLand(sender, "ADMIN_LAND_FORCE_DELETE", null, land,
                "Force-deleted land without confirmation; prior bank="
                        + plugin.getEconomy().format(previousBalance));
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-land.force-delete-success",
                "&aForce-deleted {land}. Prior bank balance={balance} was discarded.",
                "land", land.getDisplayName(),
                "balance", plugin.getEconomy().format(previousBalance));
        return true;
    }

    private boolean forceSetSpawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 3) {
            msg(sender, "admin-land.force-setspawn-usage",
                    "&eUsage: /land admin-land-force setspawn <land>");
            return true;
        }
        Land land = resolve(sender, args[2]);
        if (land == null) {
            sendContextLandError(sender, args[2]);
            return true;
        }
        if (!plugin.getLandManager().setHome(
                player, land, player.getLocation(), false)) {
            msg(sender, "admin-land.force-setspawn-failed",
                    "&cCould not set the spawn. The location must be in the same world as {land}.",
                    "land", land.getDisplayName());
            return true;
        }
        auditLand(sender, "ADMIN_LAND_FORCE_SET_SPAWN", null, land,
                "Set spawn outside the land boundary");
        plugin.requestSaveAsync(true);
        msg(sender, "admin-land.force-setspawn-success",
                "&aSet the spawn for {land} to the administrator's current location.",
                "land", land.getDisplayName());
        return true;
    }

    private boolean forceTeleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 3) {
            msg(sender, "admin-land.force-teleport-usage",
                    "&eUsage: /land admin-land-force spawn|tp <land>");
            return true;
        }
        Land land = resolve(sender, args[2]);
        if (land == null) {
            sendContextLandError(sender, args[2]);
            return true;
        }
        org.bukkit.Location target =
                plugin.getLandManager().getTeleportLocation(land);
        if (target == null) {
            msg(sender, "spawn.world-unavailable",
                    "&cThe target world is unavailable.");
            return true;
        }
        player.teleport(target);
        auditLand(sender, "ADMIN_LAND_FORCE_TELEPORT", null, land,
                "Force-teleported without fee-debt checks");
        msg(sender, "spawn.teleport", "&aTeleported to {land}.",
                "land", land.getDisplayName());
        return true;
    }

    private void showUsage(CommandSender sender, boolean force) {
        msg(sender, force ? "admin-land.force-usage" : "admin-land.usage",
                force
                        ? "&eUsage: /land admin-land-force <command> ..."
                        : "&eUsage: /land admin-land <command> ...");
        msg(sender, "admin-land.commands",
                "&7Available: create, claim, unclaim, subcreate, info, list, flags, flag, add, kick, perm, rename, setowner, delete, setspawn, spawn, tp, bank, bank-all, rent, logs");
    }

    private String[] tail(String[] args, String first) {
        String[] result = new String[Math.max(1, args.length - 1)];
        result[0] = first;
        if (args.length > 2) {
            System.arraycopy(args, 2, result, 1, args.length - 2);
        }
        return result;
    }
}
