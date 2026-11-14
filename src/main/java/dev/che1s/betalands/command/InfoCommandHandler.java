package dev.che1s.betalands.command;

import java.util.List;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.Text;

final class InfoCommandHandler extends CommandSupport {
    InfoCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "list".equals(subcommand) || "info".equals(subcommand)
                || "playtime".equals(subcommand) || "level".equals(subcommand)
                || "logs".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args) {
        if ("list".equals(subcommand)) {
            return list(sender, args);
        }
        if ("info".equals(subcommand)) {
            return info(sender, args);
        }
        if ("playtime".equals(subcommand)) {
            return playtime(sender, args);
        }
        if ("level".equals(subcommand)) {
            return ownLevel(sender, args);
        }
        if ("logs".equals(subcommand)) {
            return logs(sender, args);
        }
        return false;
    }

    private boolean list(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1]
                : (sender instanceof Player ? sender.getName() : null);
        if (target == null) {
            msg(sender, "info.console-player",
                    "&cConsole must specify a player name.");
            return true;
        }
        List<Land> owned = plugin.getLandManager().getOwnedMainLands(target);
        List<Land> accessible =
                plugin.getLandManager().getAccessibleLands(target);
        msg(sender, "info.list-header", "&6Lands owned by {player}",
                "player", target);
        if (owned.isEmpty()) {
            msg(sender, "info.list-empty", "&7No main lands.");
        } else {
            for (Land land : owned) {
                List<Land> children =
                        plugin.getLandManager().getChildren(land.getId());
                msg(sender, "info.list-land",
                        "&b  {land} &7[{world}, {area} blocks, {subclaims} subclaims]",
                        "land", land.getDisplayName(),
                        "world", land.getWorld(),
                        "area", Long.toString(land.area()),
                        "subclaims", Integer.toString(children.size()));
                for (Land child : children) {
                    msg(sender, "info.list-subclaim",
                            "&7    - {land} Y={minY}..{maxY}",
                            "land", child.getDisplayName(),
                            "minY", Integer.toString(child.getMinY()),
                            "maxY", Integer.toString(child.getMaxY()));
                }
            }
        }
        if (accessible.size() > owned.size()) {
            msg(sender, "info.list-extra",
                    "&e{count} additional joined or trusted lands.",
                    "count", Integer.toString(accessible.size() - owned.size()));
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        Land land = null;
        if (args.length >= 2) {
            land = resolve(sender, args[1]);
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            land = plugin.getLandManager().findLandAt(player.getLocation());
            if (land == null) {
                land = resolveSingleOwnedMain(player);
            }
        }
        if (land == null) {
            msg(sender, "info.no-current",
                    "&eNo land here. Use /land info <land>.");
            return true;
        }
        msg(sender, "info.header", "&6{land}",
                "land", land.getDisplayName());
        msg(sender, "info.owner", "&7Owner: {owner} | World: {world}",
                "owner", land.getOwner(), "world", land.getWorld());
        if (land.isSubclaim()) {
            msg(sender, "info.type-sub",
                    "&7Type: 3D subclaim | Y={minY}..{maxY} | Area={area}",
                    "minY", Integer.toString(land.getMinY()),
                    "maxY", Integer.toString(land.getMaxY()),
                    "area", Long.toString(land.area()));
        } else {
            msg(sender, "info.type-main",
                    "&7Type: 2D main land | Area={area}",
                    "area", Long.toString(land.area()));
        }
        Set<String> joined = plugin.getLandManager().getJoinedMembers(land);
        msg(sender, "info.joined-count",
                "&7Players with explicit build access: {count}",
                "count", Integer.toString(joined.size()));
        if (!joined.isEmpty()) {
            msg(sender, "info.members", "&7Members: {members}",
                    "members", String.join(", ", joined));
        }
        return true;
    }

    private boolean playtime(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1]
                : (sender instanceof Player ? sender.getName() : null);
        if (target == null) {
            msg(sender, "common.console-player",
                    "&cConsole must specify a player name.");
            return true;
        }
        PlayerData data = plugin.getLandManager().getPlayerData(target);
        long seconds = data == null ? 0L
                : data.getTotalSeconds(System.currentTimeMillis());
        long required = plugin.getSettings().getOutsideBuildHours() * 3600L;
        msg(sender, "playtime.result",
                "&6{player} online time: {time}",
                "player", target, "time", Text.duration(seconds));
        if (required <= 0L || seconds >= required) {
            msg(sender, "playtime.unlocked",
                    "&7Outside building: unlocked");
        } else {
            msg(sender, "playtime.locked",
                    "&7Outside building: locked; remaining {remaining}",
                    "remaining", Text.duration(required - seconds));
        }
        return true;
    }

    private boolean ownLevel(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length > 1) {
            msg(sender, "level.self-usage",
                    "&eUsage: /land level");
            return true;
        }
        showLevel(sender, player);
        return true;
    }

    void showLevel(CommandSender sender, Player target) {
        showLevel(sender, target.getName());
    }

    void showLevel(CommandSender sender, String playerName) {
        Player online = findOnlinePlayer(playerName);
        PlayerData data =
                plugin.getLandManager().getPlayerData(playerName);
        String resolvedName = online != null ? online.getName()
                : data != null && data.getLastName() != null
                && data.getLastName().length() > 0
                ? data.getLastName() : playerName;
        LevelProfile profile =
                plugin.getLevels().getProfileForName(resolvedName);
        msg(sender, "level.header", "&6{player} land level: {level} ({id})",
                "player", resolvedName,
                "level", profile.getDisplayName(), "id", profile.getId());
        LevelSource source = levelSource(online, data);
        if (source == LevelSource.MANAGED) {
            String expires = data.getLevelExpiresAt() <= 0L ? "permanent"
                    : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new java.util.Date(data.getLevelExpiresAt()));
            msg(sender, "level.managed",
                    "&7Source: BetaLands roster | Expires: {expires}",
                    "expires", expires);
        } else if (source == LevelSource.ONLINE_PERMISSION) {
            msg(sender, "level.permission",
                    "&7Source: Bukkit level permission");
        } else if (source == LevelSource.OFFLINE_SNAPSHOT) {
            msg(sender, "level.offline-snapshot",
                    "&7Source: Stored offline snapshot (last known level)");
        } else {
            msg(sender, "level.offline-default",
                    "&7Source: Default (no stored offline level snapshot)");
        }
        msg(sender, "level.limits-v2",
                "&7Main lands={main}, chunks per main={chunks}, subclaims per main={sub}, family members={members}, joined lands={joined}",
                "main", Integer.toString(profile.getMaxMainLands()),
                "chunks", Integer.toString(profile.getMaxMainChunks()),
                "sub", Integer.toString(profile.getMaxSubclaimsPerMain()),
                "members", Integer.toString(profile.getMaxFamilyMembers()),
                "joined", Integer.toString(profile.getMaxJoinedLands()));
        msg(sender, "level.prices-scaled",
                "&7Create={create}, claim={claim}+{increment}*existing-chunks, subclaim/block={subBlock}",
                "create", plugin.getEconomy().format(
                        profile.getCreateMainPrice()),
                "claim", plugin.getEconomy().format(
                        profile.getClaimChunkPrice()),
                "increment", plugin.getEconomy().format(
                        profile.getClaimChunkIncrement()),
                "subBlock", plugin.getEconomy().format(
                        profile.getSubclaimPerBlockPrice()));
        msg(sender, "level.upkeep-v3",
                "&7Maintenance: {chunkRate}/chunk/cycle + {memberRate}/member/cycle; charged every {days} days.",
                "chunkRate", plugin.getEconomy().format(
                        profile.getMaintenancePerChunkPerCycle()),
                "memberRate", plugin.getEconomy().format(
                        profile.getMaintenancePerMemberPerCycle()),
                "days", Integer.toString(profile.getMaintenanceIntervalDays()));
    }

    static LevelSource levelSource(Player online, PlayerData data) {
        if (data != null && data.isLevelManaged()) {
            return LevelSource.MANAGED;
        }
        if (online != null) {
            return LevelSource.ONLINE_PERMISSION;
        }
        return data == null
                ? LevelSource.OFFLINE_DEFAULT : LevelSource.OFFLINE_SNAPSHOT;
    }

    enum LevelSource {
        MANAGED,
        ONLINE_PERMISSION,
        OFFLINE_SNAPSHOT,
        OFFLINE_DEFAULT
    }

    private boolean logs(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_LOGS)) {
            noPermission(sender);
            return true;
        }
        Land land;
        String limitText;
        if (args.length >= 3) {
            land = resolve(sender, args[2]);
            limitText = args[1];
        } else if (args.length == 2 && !isInteger(args[1])) {
            land = resolve(sender, args[1]);
            limitText = null;
        } else {
            land = resolveManageableContextLand(actor);
            limitText = args.length >= 2 ? args[1] : null;
        }
        if (land == null) {
            sendContextLandError(sender, args.length >= 3 ? args[2]
                    : (args.length == 2 ? args[1] : null));
            return true;
        }
        int limit = 50;
        if (limitText != null) {
            try {
                limit = Integer.parseInt(limitText);
            } catch (NumberFormatException ignored) {
            }
        }
        if (!plugin.getLandManager().canManage(actor, land)
                && !plugin.getLandManager().isAdmin(actor)) {
            msg(sender, "member.scoped-logs-denied",
                    "&cOnly the land owner or a land manager can view these logs.");
            return true;
        }
        final String landName = land.getDisplayName();
        boolean queued = plugin.getAuditLog().readLandRecentAsync(
                land, limit,
                !plugin.getSettings().shouldHideAdminLogsFromPlayers()
                        || plugin.getLandManager().isAdmin(actor),
                new java.util.function.Consumer<List<String>>() {
                    @Override
                    public void accept(final List<String> lines) {
                        runOnServerThread(new Runnable() {
                            @Override
                            public void run() {
                                sendAuditLines(sender,
                                        "logs.land-header",
                                        "&6{land} recent logs",
                                        lines, "land", landName);
                            }
                        });
                    }
                });
        if (!queued) {
            msg(sender, "logs.unavailable",
                    "&cAudit log database is unavailable.");
        }
        return true;
    }
}
