package dev.che1s.betalands.command;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;

abstract class CommandSupport {
    protected final BetaLandsPlugin plugin;

    CommandSupport(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    protected void msg(CommandSender sender, String key, String fallback,
                       String... replacements) {
        plugin.getMessages().send(sender, key, fallback, replacements);
    }

    protected Land resolve(CommandSender sender, String reference) {
        return plugin.getLandManager().resolveRegion(reference,
                sender instanceof Player ? sender.getName() : null);
    }

    protected Land resolveManageableContextLand(Player player) {
        Land current = plugin.getLandManager().findLandAt(player.getLocation());
        if (current != null
                && plugin.getLandManager().canManage(player, current)) {
            return current;
        }
        return resolveSingleOwnedMain(player);
    }

    protected Land resolveSingleOwnedMain(Player player) {
        List<Land> owned = plugin.getLandManager()
                .getOwnedMainLands(player.getName());
        return owned.size() == 1 ? owned.get(0) : null;
    }

    protected Land resolveMainContextLand(Player player) {
        Land current = plugin.getLandManager().findLandAt(player.getLocation());
        if (current != null) {
            Land main = current.isSubclaim()
                    ? plugin.getLandManager().getById(current.getParentId())
                    : current;
            if (main != null
                    && plugin.getLandManager().canManage(player, main)) {
                return main;
            }
        }
        return resolveSingleOwnedMain(player);
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        msg(sender, "common.player-only",
                "&cThis command can only be used by a player.");
        return null;
    }

    protected void refreshMaps() {
        plugin.scheduleMapRefresh();
    }

    protected void noPermission(CommandSender sender) {
        msg(sender, "common.no-permission",
                "&cYou do not have permission.");
    }

    protected boolean hasPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return plugin.getLandManager().hasActionPermission(
                (Player) sender, permission);
    }

    protected boolean hasUsePermission(CommandSender sender) {
        return hasPermission(sender, PermissionNodes.USE);
    }

    protected void sendContextLandError(CommandSender sender, String reference) {
        if (reference != null && reference.length() > 0) {
            msg(sender, "common.land-not-found",
                    "&cLand not found: {land}", "land", reference);
            return;
        }
        msg(sender, "common.land-context-required",
                "&cCould not choose a land. Stand inside a land you manage or specify the land name.");
    }

    protected Player findOnlinePlayer(String name) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    protected boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    protected void auditLand(CommandSender sender, String action, String target,
                             Land land, String details) {
        if (plugin.getAuditLog() != null) {
            plugin.getAuditLog().logLand(
                    action, sender.getName(), target, land, details,
                    isPrivileged(sender));
        }
    }

    protected void auditEconomy(CommandSender sender, String action, Land land,
                                double amount, Double balance, String details) {
        if (plugin.getAuditLog() != null && amount > 0.0D) {
            plugin.getAuditLog().logEconomy(
                    action, sender.getName(), land, amount, balance, details,
                    isPrivileged(sender));
        }
    }

    private boolean isPrivileged(CommandSender sender) {
        return !(sender instanceof Player)
                || plugin.getLandManager().isAdmin((Player) sender);
    }

    protected String costSuffix(double price) {
        return price <= 0.0D ? ""
                : ", cost " + plugin.getEconomy().format(price);
    }

    protected void sendAuditLines(CommandSender sender, String headerKey,
                                  String headerFallback, List<String> lines,
                                  String... replacements) {
        msg(sender, headerKey, headerFallback, replacements);
        for (String line : lines) {
            sender.sendMessage(ChatColor.GRAY + clientSafe(line));
        }
    }

    protected void runOnServerThread(Runnable task) {
        plugin.getServer().getScheduler()
                .scheduleSyncDelayedTask(plugin, task, 0L);
    }

    protected String clientSafe(String value) {
        return value == null ? ""
                : value.replaceAll("[^\\x20-\\x7E\\t]", "");
    }
}
