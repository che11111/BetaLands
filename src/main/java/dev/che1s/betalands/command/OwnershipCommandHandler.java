package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;

final class OwnershipCommandHandler extends CommandSupport {
    OwnershipCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "rename".equals(subcommand) || "setowner".equals(subcommand)
                || "delete".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args) {
        if ("rename".equals(subcommand)) {
            return rename(sender, args);
        }
        if ("setowner".equals(subcommand)) {
            return transferOwner(sender, args);
        }
        if ("delete".equals(subcommand)) {
            return delete(sender, args);
        }
        return false;
    }

    private boolean rename(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_RENAME)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "ownership.rename-usage-v2",
                    "&eUsage: /land rename <new-name> [land]");
            return true;
        }
        Land land = args.length == 2
                ? resolveManageableContextLand(actor) : resolve(sender, args[2]);
        if (land == null) {
            sendContextLandError(sender, args.length >= 3 ? args[2] : null);
            return true;
        }
        String oldName = land.getTerritoryName();
        String error = plugin.getLandManager().renameLand(actor, land, args[1]);
        if (error != null) {
            msg(sender, "ownership.rename-failed",
                    "&cRename failed: {reason}", "reason", error);
            return true;
        }
        msg(sender, "ownership.rename-success",
                "&aRenamed {old} to {land}.",
                "old", oldName, "land", land.getTerritoryName());
        auditLand(sender, "RENAME_LAND", null, land,
                "Renamed " + oldName + " to " + land.getTerritoryName());
        refreshMaps();
        return true;
    }

    private boolean transferOwner(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_SETOWNER)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "ownership.owner-usage-v2",
                    "&eUsage: /land setowner <online-player> [main-land]");
            return true;
        }
        Land land = args.length == 2
                ? resolveMainContextLand(actor) : resolve(sender, args[2]);
        if (land == null) {
            sendContextLandError(sender, args.length >= 3 ? args[2] : null);
            return true;
        }
        Player target = findOnlinePlayer(args[1]);
        if (target == null) {
            msg(sender, "common.target-offline", "&cTarget player is offline.");
            return true;
        }
        String error = plugin.getLandManager()
                .transferHighestOwner(actor, land, target);
        if (error != null) {
            msg(sender, "ownership.owner-failed",
                    "&cOwnership transfer failed: {reason}",
                    "reason", error);
            return true;
        }
        msg(sender, "ownership.owner-success",
                "&aTransferred {land} to {player}.",
                "land", land.getTerritoryName(), "player", target.getName());
        msg(target, "ownership.owner-received",
                "&aYou are now the highest owner of {land}.",
                "land", land.getTerritoryName());
        auditLand(sender, "TRANSFER_OWNER", target.getName(), land,
                "Transferred highest ownership to " + target.getName());
        refreshMaps();
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_DELETE)) {
            noPermission(sender);
            return true;
        }
        boolean confirmed = args.length >= 2
                && "confirm".equalsIgnoreCase(args[1]);
        if (!confirmed) {
            if (args.length > 2) {
                msg(sender, "delete.usage-v3",
                        "&eUsage: /land delete [land]");
                return true;
            }
            Land land = args.length == 2 ? resolve(sender, args[1])
                    : resolveManageableContextLand(actor);
            if (land == null) {
                sendContextLandError(sender, args.length == 2 ? args[1] : null);
                return true;
            }
            if (!plugin.getLandManager().canDelete(actor, land)) {
                msg(sender, "delete.failed",
                        "&cDelete failed: land not found or you are not the owner.");
                return true;
            }
            if (showDeleteBankRequirement(sender, land)) {
                return true;
            }
            String confirmCommand = "/land delete confirm "
                    + land.getDisplayName();
            msg(sender, "delete.confirm-required",
                    "&cDeleting {land} is permanent and also deletes all subclaims. To confirm, run {command}.",
                    "land", land.getDisplayName(),
                    "command", confirmCommand);
            return true;
        }
        if (!deleteConfirmationHasTarget(args)) {
            msg(sender, "delete.confirm-usage",
                    "&eUsage: /land delete confirm <land>");
            return true;
        }
        Land land = args.length == 3 ? resolve(sender, args[2])
                : resolveManageableContextLand(actor);
        if (land == null) {
            sendContextLandError(sender, args.length == 3 ? args[2] : null);
            return true;
        }
        if (showDeleteBankRequirement(sender, land)) {
            return true;
        }
        if (!plugin.getLandManager().deleteLand(actor, land)) {
            msg(sender, "delete.failed-v2",
                    "&cDelete failed: you are not the owner or the land bank is not zero.");
            return true;
        }
        msg(sender, "delete.success", "&aDeleted {land}.",
                "land", land.getDisplayName());
        auditLand(sender, "DELETE_LAND", null, land, "Deleted land");
        plugin.requestSaveAsync(true);
        refreshMaps();
        return true;
    }

    private boolean showDeleteBankRequirement(CommandSender sender, Land land) {
        Land main = plugin.getMaintenanceService().getBankLand(land);
        if (main == null) {
            return false;
        }
        double balance = main.getBankBalance();
        if (!dev.che1s.betalands.service.ClaimService
                .hasZeroBankBalance(balance) && balance < 0.0D) {
            msg(sender, "delete.bank-debt",
                    "&cCannot delete {land}: the main-land bank is {balance}. Deposit {amount} with {command} to clear the debt.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance),
                    "amount", plugin.getEconomy().format(-balance),
                    "command", "/land bank deposit "
                            + plugin.getEconomy().format(-balance)
                            + " " + main.getDisplayName());
            return true;
        }
        if (!dev.che1s.betalands.service.ClaimService
                .hasZeroBankBalance(balance) && balance > 0.0D) {
            msg(sender, "delete.bank-positive",
                    "&eCannot delete {land}: the main-land bank is {balance}. Withdraw {amount} with {command} first.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance),
                    "amount", plugin.getEconomy().format(balance),
                    "command", "/land bank withdraw "
                            + plugin.getEconomy().format(balance)
                            + " " + main.getDisplayName());
            return true;
        }
        return false;
    }

    static boolean deleteConfirmationHasTarget(String[] args) {
        return args != null && args.length == 3
                && args[1] != null && "confirm".equalsIgnoreCase(args[1])
                && args[2] != null && args[2].trim().length() > 0;
    }
}
