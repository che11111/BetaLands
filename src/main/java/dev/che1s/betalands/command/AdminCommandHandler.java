package dev.che1s.betalands.command;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.JarHash;
import dev.che1s.betalands.util.Text;

final class AdminCommandHandler extends CommandSupport {
    private final PlayerCommandHandler playerCommands;
    private final TownyMigrationCommandHandler migrationCommands;

    AdminCommandHandler(BetaLandsPlugin plugin,
                        PlayerCommandHandler playerCommands) {
        super(plugin);
        this.playerCommands = playerCommands;
        this.migrationCommands = new TownyMigrationCommandHandler(plugin);
    }

    boolean handle(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission(PermissionNodes.ADMIN)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "admin.usage-v4",
                    "&eUsage: /land admin <tp|version|level|refresh-map|server-bank|maintenance-exempt|setlevel|reload|save|status|logs|migrate-towny>");
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("version".equals(action)) {
            showVersion(sender);
            return true;
        }
        if ("level".equals(action)) {
            return showLevel(sender, args);
        }
        if ("tp".equals(action)) {
            return teleport(sender, args);
        }
        if ("setlevel".equals(action)) {
            return setManagedLevel(sender, args);
        }
        if ("server-bank".equals(action)) {
            msg(sender, "admin.server-bank",
                    "&6Server maintenance bank: {balance}",
                    "balance", plugin.getEconomy().format(
                            plugin.getLandManager().getServerMaintenanceBank()));
            return true;
        }
        if ("refresh-map".equals(action)) {
            MapRefreshTarget target = parseRefreshTarget(args);
            if (target == null) {
                msg(sender, "map.refresh-usage",
                        "&eUsage: /land admin refresh-map [dynmap|bluemap|all]");
                return true;
            }
            if ((target == MapRefreshTarget.DYNMAP
                    && !plugin.getSettings().isDynmapEnabled())
                    || (target == MapRefreshTarget.BLUEMAP
                    && !plugin.getSettings().isBluemapEnabled())
                    || (target == MapRefreshTarget.ALL
                    && !plugin.getSettings().isDynmapEnabled()
                    && !plugin.getSettings().isBluemapEnabled())) {
                msg(sender, "map.refresh-disabled",
                        "&eThe requested map integration is disabled in config.yml.");
                return true;
            }
            if (target == MapRefreshTarget.DYNMAP
                    || target == MapRefreshTarget.ALL) {
                plugin.refreshDynmapNow();
            }
            if (target == MapRefreshTarget.BLUEMAP
                    || target == MapRefreshTarget.ALL) {
                plugin.refreshBluemapNow();
            }
            msg(sender, "map.refreshed-now",
                    "&aImmediate refresh queued for {target}.",
                    "target", target.id);
            return true;
        }
        if ("maintenance-exempt".equals(action)) {
            return setMaintenanceExemption(sender, args);
        }
        if ("migrate-towny".equals(action)) {
            return migrationCommands.handle(sender, args);
        }
        if ("reload".equals(action)) {
            plugin.reloadPlugin();
            msg(sender, "admin.reloaded-v2",
                    "&aConfiguration, level data and reloadable integrations refreshed.");
            return true;
        }
        if ("save".equals(action)) {
            if (plugin.requestSaveAsync(true)) {
                msg(sender, "admin.saved",
                        "&aBackground save queued. Any errors will be written to the console.");
            } else {
                msg(sender, "admin.save-failed",
                        "&cSave failed. Check the console.");
            }
            return true;
        }
        if ("status".equals(action)) {
            showStatus(sender);
            return true;
        }
        if ("logs".equals(action)) {
            return showLogs(sender, args);
        }
        msg(sender, "admin.unknown", "&cUnknown admin command.");
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 5 && args.length != 6) {
            msg(sender, "admin.teleport-usage",
                    "&eUsage: /land admin tp <x> <y> <z> [world]");
            return true;
        }
        Double x = coordinate(args[2]);
        Double y = coordinate(args[3]);
        Double z = coordinate(args[4]);
        if (x == null || y == null || z == null) {
            msg(sender, "admin.teleport-invalid-coordinate",
                    "&cCoordinates must be finite numbers.");
            return true;
        }
        String requestedWorld = teleportWorldName(args);
        org.bukkit.World world = requestedWorld == null
                ? player.getWorld() : findWorld(requestedWorld);
        if (world == null) {
            msg(sender, "admin.teleport-world-missing",
                    "&cWorld not found: {world}",
                    "world", requestedWorld);
            return true;
        }
        if (y.doubleValue() < 0.0D
                || y.doubleValue() >= world.getMaxHeight()) {
            msg(sender, "admin.teleport-invalid-height",
                    "&cY must be between 0 and {maxY}.",
                    "maxY", Integer.toString(world.getMaxHeight() - 1));
            return true;
        }
        org.bukkit.Location target = new org.bukkit.Location(
                world, x.doubleValue(), y.doubleValue(), z.doubleValue());
        player.teleport(target);
        msg(sender, "admin.teleport-success",
                "&aTeleported to {world} at {x}, {y}, {z}.",
                "world", world.getName(),
                "x", formatCoordinate(x.doubleValue()),
                "y", formatCoordinate(y.doubleValue()),
                "z", formatCoordinate(z.doubleValue()));
        return true;
    }

    private org.bukkit.World findWorld(String name) {
        org.bukkit.World direct = plugin.getServer().getWorld(name);
        if (direct != null) {
            return direct;
        }
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            if (world.getName().equalsIgnoreCase(name)) {
                return world;
            }
        }
        return null;
    }

    static Double coordinate(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isNaN(parsed) || Double.isInfinite(parsed)
                    ? null : Double.valueOf(parsed);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String teleportWorldName(String[] args) {
        return args != null && args.length == 6 ? args[5] : null;
    }

    static MapRefreshTarget parseRefreshTarget(String[] args) {
        if (args == null || args.length > 3) {
            return null;
        }
        if (args.length < 3) {
            return MapRefreshTarget.ALL;
        }
        String value = args[2] == null
                ? "" : args[2].toLowerCase(java.util.Locale.ROOT);
        if ("dynmap".equals(value)) {
            return MapRefreshTarget.DYNMAP;
        }
        if ("bluemap".equals(value)) {
            return MapRefreshTarget.BLUEMAP;
        }
        if ("all".equals(value)) {
            return MapRefreshTarget.ALL;
        }
        return null;
    }

    private String formatCoordinate(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private boolean showLevel(CommandSender sender, String[] args) {
        if (args.length > 3) {
            msg(sender, "admin.level-usage",
                    "&eUsage: /land admin level [player]");
            return true;
        }
        String targetName;
        if (args.length == 3) {
            targetName = args[2];
            if (!Text.validName(targetName, 2, 16)) {
                msg(sender, "common.invalid-player-name",
                        "&cPlayer names may only contain letters, numbers, underscores, and hyphens.");
                return true;
            }
        } else if (sender instanceof Player) {
            targetName = sender.getName();
        } else {
            msg(sender, "common.console-player",
                    "&cConsole must specify a player name.");
            return true;
        }
        playerCommands.showLevel(sender, targetName);
        return true;
    }

    private void showVersion(CommandSender sender) {
        File jar = plugin.getPluginJarFile();
        msg(sender, "admin.version-header", "&6BetaLands version");
        msg(sender, "admin.version-value", "&7Version: &a{version}",
                "version", plugin.getDescription().getVersion());
        msg(sender, "admin.version-jar", "&7Jar: &f{file}",
                "file", jar == null ? "unavailable" : jar.getName());
        try {
            msg(sender, "admin.version-hash-value", "&7SHA-256: &f{hash}",
                    "hash", JarHash.sha256(jar));
        } catch (IOException exception) {
            msg(sender, "admin.version-hash-failed",
                    "&cCould not calculate JAR SHA-256: {error}",
                    "error", exception.getMessage());
        }
        msg(sender, "admin.version-dynmap", "&7Dynmap: &f{status}",
                "status", plugin.getMapService() == null
                        ? "map-service-unavailable"
                        : plugin.getMapService().dynmapDiagnostic());
        msg(sender, "admin.version-bluemap", "&7BlueMap: &f{status}",
                "status", plugin.getMapService() == null
                        ? "map-service-unavailable"
                        : plugin.getMapService().blueMapDiagnostic());
        msg(sender, "admin.version-iconomy", "&7iConomy: &f{status}",
                "status", plugin.getEconomy().diagnostic());
    }

    private boolean setMaintenanceExemption(CommandSender sender,
                                            String[] args) {
        if (args.length < 3 || args.length > 4) {
            msg(sender, "admin.maintenance-exempt-usage",
                    "&eUsage: /land admin maintenance-exempt <true|false> [land]");
            return true;
        }
        Boolean exempt;
        if ("true".equalsIgnoreCase(args[2])
                || "on".equalsIgnoreCase(args[2])
                || "yes".equalsIgnoreCase(args[2])) {
            exempt = Boolean.TRUE;
        } else if ("false".equalsIgnoreCase(args[2])
                || "off".equalsIgnoreCase(args[2])
                || "no".equalsIgnoreCase(args[2])) {
            exempt = Boolean.FALSE;
        } else {
            msg(sender, "admin.maintenance-exempt-invalid",
                    "&cValue must be true or false.");
            return true;
        }
        Land land;
        if (args.length == 4) {
            land = resolve(sender, args[3]);
        } else if (sender instanceof Player) {
            land = resolveMainContextLand((Player) sender);
        } else {
            land = null;
        }
        if (land == null) {
            sendContextLandError(sender, args.length == 4 ? args[3] : null);
            return true;
        }
        Land main = plugin.getMaintenanceService().getBankLand(land);
        if (main == null) {
            msg(sender, "bank.not-found",
                    "&cNo main-land bank found.");
            return true;
        }
        plugin.getMaintenanceService().setMaintenanceExempt(
                main, exempt.booleanValue());
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin.maintenance-exempt-success",
                "&a{land}: maintenance exemption is now {state}.",
                "land", main.getDisplayName(),
                "state", exempt.booleanValue() ? "enabled" : "disabled");
        auditLand(sender, "SET_MAINTENANCE_EXEMPT", null, main,
                "Maintenance exempt=" + exempt.booleanValue());
        return true;
    }

    private boolean setManagedLevel(CommandSender sender, String[] args) {
        if (args.length < 5) {
            msg(sender, "admin.setlevel-usage",
                    "&eUsage: /land admin setlevel <player> <level> <days|permanent>");
            return true;
        }
        String targetName = args[2];
        if (!Text.validName(targetName, 2, 16)) {
            msg(sender, "common.invalid-player-name",
                    "&cPlayer names may only contain letters, numbers, underscores, and hyphens.");
            return true;
        }
        LevelProfile profile = plugin.getLevels()
                .getProfileExact(args[3]);
        if (profile == null) {
            msg(sender, "admin.setlevel-invalid",
                    "&cUnknown level: {level}", "level", args[3]);
            return true;
        }
        long expiresAt;
        if ("permanent".equalsIgnoreCase(args[4])) {
            expiresAt = 0L;
        } else {
            long days;
            try {
                days = Long.parseLong(args[4]);
            } catch (NumberFormatException exception) {
                days = 0L;
            }
            if (days <= 0L) {
                msg(sender, "admin.setlevel-days",
                        "&cDuration must be a positive number of days or permanent.");
                return true;
            }
            long duration = days * 86400000L;
            expiresAt = duration <= 0L
                    ? Long.MAX_VALUE : System.currentTimeMillis() + duration;
        }
        LevelProfile granted = plugin.getLevels()
                .grantManagedLevel(targetName, profile.getId(), expiresAt);
        if (granted == null) {
            msg(sender, "admin.setlevel-invalid",
                    "&cUnknown level: {level}", "level", args[3]);
            return true;
        }
        Player target = findOnlinePlayer(targetName);
        if (target != null) {
            plugin.getLandManager().syncPlayerLevel(target);
        }
        plugin.requestSaveAsync(true);
        String durationText = expiresAt == 0L ? "permanent"
                : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                .format(new java.util.Date(expiresAt));
        msg(sender, "admin.setlevel-success",
                "&aSet {player}'s managed level to {level} ({duration}). Background saving was queued.",
                "player", targetName,
                "level", granted.getDisplayName(),
                "duration", durationText);
        auditLand(sender, "SET_MANAGED_LEVEL", targetName, null,
                "Level=" + granted.getId() + ", expires=" + durationText);
        return true;
    }

    private void showStatus(CommandSender sender) {
        msg(sender, "admin.status-header", "&6BetaLands status");
        msg(sender, "admin.status-counts",
                "&7Lands={lands}, players={players}, outside unlock={hours} hours",
                "lands", Integer.toString(
                        plugin.getLandManager().getLands().size()),
                "players", Integer.toString(
                        plugin.getLandManager().getPlayers().size()),
                "hours", Long.toString(
                        plugin.getSettings().getOutsideBuildHours()));
        LevelProfile profile = plugin.getLevels().getDefaultProfile();
        msg(sender, "admin.status-economy-scaled",
                "&7Economy={economy}, default level={level}, levels={levels}, create={create}, claim={claim}+{increment}*chunks, subclaim/block={subBlock}",
                "economy", plugin.getEconomy().getProviderName(),
                "level", profile.getDisplayName(),
                "levels", Integer.toString(
                        plugin.getLevels().getProfiles().size()),
                "create", plugin.getEconomy().format(
                        profile.getCreateMainPrice()),
                "claim", plugin.getEconomy().format(
                        profile.getClaimChunkPrice()),
                "increment", plugin.getEconomy().format(
                        profile.getClaimChunkIncrement()),
                "subBlock", plugin.getEconomy().format(
                        profile.getSubclaimPerBlockPrice()));
    }

    private boolean showLogs(CommandSender sender, String[] args) {
        LogQuery query = parseLogQuery(args);
        if (!query.isValid()) {
            msg(sender, "admin.logs-usage",
                    "&eUsage: /land admin logs [count|pN] | /land admin logs land <land> [count|pN] | /land admin logs player <player> [count|pN]");
            return true;
        }
        String filter = query.getPlayerFilter();
        int selection = query.getSelection();
        boolean pageMode = query.isPageMode();
        String selectionText = pageMode
                ? "page " + selection : "latest " + selection;
        if (query.getMode() == LogMode.LAND) {
            Land land = resolve(sender, filter);
            if (land == null) {
                msg(sender, "common.land-not-found",
                        "&cLand not found: {land}", "land", filter);
                return true;
            }
            final String landName = land.getDisplayName();
            boolean queued = pageMode
                    ? plugin.getAuditLog().readLandRecentPageAsync(
                    land, selection,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(final List<String> lines) {
                            runOnServerThread(new Runnable() {
                                @Override
                                public void run() {
                                    sendAuditLines(sender,
                                            "logs.admin-land-header",
                                            "&6Recent logs for {land} | {selection}",
                                            lines, "land", landName,
                                            "selection", selectionText);
                                }
                            });
                        }
                    })
                    : plugin.getAuditLog().readLandRecentAsync(
                    land, selection, true,
                    new java.util.function.Consumer<List<String>>() {
                        @Override
                        public void accept(final List<String> lines) {
                            runOnServerThread(new Runnable() {
                                @Override
                                public void run() {
                                    sendAuditLines(sender,
                                            "logs.admin-land-header",
                                            "&6Recent logs for {land} | {selection}",
                                            lines, "land", landName,
                                            "selection", selectionText);
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
        final String filterText = filter == null ? ""
                : " (player=" + filter + ")";
        boolean queued = pageMode
                ? plugin.getAuditLog().readRecentPageAsync(
                selection, filter,
                new java.util.function.Consumer<List<String>>() {
                    @Override
                    public void accept(final List<String> lines) {
                        runOnServerThread(new Runnable() {
                            @Override
                            public void run() {
                                sendAuditLines(sender,
                                        "logs.admin-header",
                                        "&6BetaLands logs | {selection}{filter}",
                                        lines, "selection", selectionText,
                                        "filter", filterText);
                            }
                        });
                    }
                })
                : plugin.getAuditLog().readRecentAsync(
                selection, filter,
                new java.util.function.Consumer<List<String>>() {
                    @Override
                    public void accept(final List<String> lines) {
                        runOnServerThread(new Runnable() {
                            @Override
                            public void run() {
                                sendAuditLines(sender,
                                        "logs.admin-header",
                                        "&6BetaLands logs | {selection}{filter}",
                                        lines, "selection", selectionText,
                                        "filter", filterText);
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

    static LogQuery parseLogQuery(String[] args) {
        if (args == null || args.length < 2 || args.length > 5) {
            return LogQuery.invalid();
        }
        if (args.length == 2) {
            return LogQuery.all(50, false);
        }
        if (args.length == 3) {
            return parseSelection(args[2],
                    new SelectionFactory() {
                        @Override
                        public LogQuery create(int value, boolean pageMode) {
                            return LogQuery.all(value, pageMode);
                        }
                    });
        }
        LogMode mode;
        if ("land".equalsIgnoreCase(args[2])) {
            mode = LogMode.LAND;
        } else if ("player".equalsIgnoreCase(args[2])) {
            mode = LogMode.PLAYER;
        } else {
            return LogQuery.invalid();
        }
        if (args[3] == null || args[3].length() == 0) {
            return LogQuery.invalid();
        }
        int selection = 50;
        boolean pageMode = false;
        if (args.length == 5) {
            LogQuery selected = parseSelection(args[4],
                    new SelectionFactory() {
                        @Override
                        public LogQuery create(int value, boolean page) {
                            return new LogQuery(mode, args[3], value, page, true);
                        }
                    });
            if (!selected.isValid()) {
                return LogQuery.invalid();
            }
            selection = selected.getSelection();
            pageMode = selected.isPageMode();
        }
        return new LogQuery(mode, args[3], selection, pageMode, true);
    }

    private static LogQuery parseSelection(String value,
                                           SelectionFactory factory) {
        if (value == null || value.length() == 0) {
            return LogQuery.invalid();
        }
        if ((value.charAt(0) == 'p' || value.charAt(0) == 'P')
                && value.length() > 1) {
            try {
                int page = Integer.parseInt(value.substring(1));
                return page > 0
                        ? factory.create(page, true) : LogQuery.invalid();
            } catch (NumberFormatException ignored) {
                return LogQuery.invalid();
            }
        }
        try {
            int count = Integer.parseInt(value);
            return count > 0 && count <= 200
                    ? factory.create(count, false) : LogQuery.invalid();
        } catch (NumberFormatException ignored) {
            return LogQuery.invalid();
        }
    }

    private interface SelectionFactory {
        LogQuery create(int value, boolean pageMode);
    }

    enum LogMode {
        ALL,
        LAND,
        PLAYER
    }

    enum MapRefreshTarget {
        DYNMAP("dynmap"),
        BLUEMAP("bluemap"),
        ALL("all");

        private final String id;

        MapRefreshTarget(String id) {
            this.id = id;
        }
    }

    static final class LogQuery {
        private final LogMode mode;
        private final String playerFilter;
        private final int selection;
        private final boolean pageMode;
        private final boolean valid;

        private LogQuery(LogMode mode, String playerFilter, int selection,
                         boolean pageMode,
                         boolean valid) {
            this.mode = mode;
            this.playerFilter = playerFilter;
            this.selection = selection;
            this.pageMode = pageMode;
            this.valid = valid;
        }

        static LogQuery all(int selection, boolean pageMode) {
            return new LogQuery(LogMode.ALL, null, selection, pageMode, true);
        }

        static LogQuery invalid() {
            return new LogQuery(LogMode.ALL, null, 0, false, false);
        }

        LogMode getMode() {
            return mode;
        }

        String getPlayerFilter() {
            return playerFilter;
        }

        int getSelection() {
            return selection;
        }

        boolean isPageMode() {
            return pageMode;
        }

        boolean isValid() {
            return valid;
        }
    }

}
