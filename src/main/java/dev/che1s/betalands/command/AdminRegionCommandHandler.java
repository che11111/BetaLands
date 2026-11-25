package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.AdminRegionPermission;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.Text;

final class AdminRegionCommandHandler extends CommandSupport {
    private static final long RESIZE_CONFIRM_TIMEOUT_MILLIS = 120000L;
    private final Map<String, PendingResize> pendingResizes =
            new HashMap<String, PendingResize>();

    AdminRegionCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.isOp()
                && !sender.hasPermission(PermissionNodes.ADMIN_PROTECT)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 3) {
            showUsage(sender);
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("create".equals(action)) {
            return create(sender, args);
        }
        if ("list".equals(action)) {
            return list(sender);
        }
        if ("info".equals(action)) {
            return info(sender, args);
        }
        if ("rename".equals(action)) {
            return rename(sender, args);
        }
        if ("delete".equals(action)) {
            return delete(sender, args);
        }
        if ("resize".equals(action)) {
            return resize(sender, args);
        }
        if ("whitelist".equals(action)) {
            return whitelist(sender, args);
        }
        if ("flag".equals(action)) {
            return flag(sender, args);
        }
        showUsage(sender);
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        if (args.length < 9) {
            msg(sender, "admin-protect.create-usage",
                    "&eUsage: /land admin protect create <name> 2d|3d <coordinates> [world]");
            return true;
        }
        String name = args[3];
        if (!validName(name)) {
            msg(sender, "admin-protect.invalid-name",
                    "&cInvalid region name.");
            return true;
        }
        if (plugin.getAdminRegions().nameExists(name, null)) {
            msg(sender, "admin-protect.name-exists",
                    "&cA protected region with that name already exists.");
            return true;
        }
        Bounds bounds = parseBounds(sender, args, 4);
        if (bounds == null) {
            return true;
        }
        AdminProtectedRegion overlapping =
                plugin.getAdminRegions().findOverlapping(bounds.world,
                        bounds.areas, bounds.minY, bounds.maxY,
                        bounds.threeDimensional, null);
        if (overlapping != null) {
            msg(sender, "admin-protect.overlap",
                    "&cProtected region overlaps {region}.",
                    "region", overlapping.getName());
            return true;
        }
        AdminProtectedRegion region = new AdminProtectedRegion(
                UUID.randomUUID().toString(), name, bounds.world,
                sender.getName(), System.currentTimeMillis(),
                bounds.threeDimensional, bounds.minY, bounds.maxY,
                bounds.areas);
        plugin.getAdminRegions().addRegion(region);
        auditLand(sender, "ADMIN_PROTECT_CREATE", null, null,
                "Created protected region " + name + " " + bounds.describe());
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-protect.created",
                "&aCreated protected region {region}: {bounds}",
                "region", region.getName(),
                "bounds", bounds.describe());
        return true;
    }

    private boolean list(CommandSender sender) {
        List<AdminProtectedRegion> regions = plugin.getAdminRegions().getRegions();
        msg(sender, "admin-protect.list-header",
                "&6Protected regions: {count}",
                "count", Integer.toString(regions.size()));
        for (AdminProtectedRegion region : regions) {
            msg(sender, "admin-protect.list-entry",
                    "&e{region} &7[{type}, {world}, whitelist={members}]",
                    "region", region.getName(),
                    "type", region.isThreeDimensional() ? "3D" : "2D",
                    "world", region.getWorld(),
                    "members", Integer.toString(
                            region.getWhitelist().size()));
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length != 4) {
            msg(sender, "admin-protect.info-usage",
                    "&eUsage: /land admin protect info <name>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[3]);
        if (region == null) {
            return true;
        }
        msg(sender, "admin-protect.info-header",
                "&6Protected region {region} ({id})",
                "region", region.getName(), "id", region.getId());
        msg(sender, "admin-protect.info-bounds",
                "&7Bounds: {bounds}",
                "bounds", describe(region));
        msg(sender, "admin-protect.info-created",
                "&7Created by {player} at {time}",
                "player", region.getCreatedBy(),
                "time", new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm").format(
                        new java.util.Date(region.getCreatedAt())));
        msg(sender, "admin-protect.info-members",
                "&7Whitelist members: {count}",
                "count", Integer.toString(region.getWhitelist().size()));
        return true;
    }

    private boolean rename(CommandSender sender, String[] args) {
        if (args.length != 5) {
            msg(sender, "admin-protect.rename-usage",
                    "&eUsage: /land admin protect rename <name> <new-name>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[3]);
        if (region == null) {
            return true;
        }
        String newName = args[4];
        if (!validName(newName)) {
            msg(sender, "admin-protect.invalid-name",
                    "&cInvalid region name.");
            return true;
        }
        if (plugin.getAdminRegions().nameExists(newName, region)) {
            msg(sender, "admin-protect.name-exists",
                    "&cA protected region with that name already exists.");
            return true;
        }
        String oldName = region.getName();
        region.rename(newName);
        plugin.getAdminRegions().rebuild();
        auditLand(sender, "ADMIN_PROTECT_RENAME", null, null,
                "Renamed protected region " + oldName + " to " + newName);
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-protect.renamed",
                "&aRenamed protected region {old} to {region}.",
                "old", oldName, "region", newName);
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (args.length != 4) {
            msg(sender, "admin-protect.delete-usage",
                    "&eUsage: /land admin protect delete <name>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[3]);
        if (region == null) {
            return true;
        }
        plugin.getAdminRegions().removeRegion(region);
        pendingResizes.remove(pendingKey(sender, region.getId()));
        auditLand(sender, "ADMIN_PROTECT_DELETE", null, null,
                "Deleted protected region " + region.getName());
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-protect.deleted",
                "&aDeleted protected region {region}.",
                "region", region.getName());
        return true;
    }

    private boolean resize(CommandSender sender, String[] args) {
        if (args.length >= 4 && "confirm".equalsIgnoreCase(args[3])) {
            return confirmResize(sender, args);
        }
        if (args.length >= 4 && "cancel".equalsIgnoreCase(args[3])) {
            return cancelResize(sender, args);
        }
        if (args.length < 9) {
            msg(sender, "admin-protect.resize-usage",
                    "&eUsage: /land admin protect resize <name> 2d|3d <coordinates> [world]");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[3]);
        if (region == null) {
            return true;
        }
        Bounds bounds = parseBounds(sender, args, 4);
        if (bounds == null) {
            return true;
        }
        AdminProtectedRegion overlapping =
                plugin.getAdminRegions().findOverlapping(bounds.world,
                        bounds.areas, bounds.minY, bounds.maxY,
                        bounds.threeDimensional, region);
        if (overlapping != null) {
            msg(sender, "admin-protect.overlap",
                    "&cProtected region overlaps {region}.",
                    "region", overlapping.getName());
            return true;
        }
        pendingResizes.put(pendingKey(sender, region.getId()),
                new PendingResize(sender.getName(), region.getId(), bounds,
                        System.currentTimeMillis()
                                + RESIZE_CONFIRM_TIMEOUT_MILLIS));
        msg(sender, "admin-protect.resize-old",
                "&7Previous bounds: {bounds}", "bounds", describe(region));
        msg(sender, "admin-protect.resize-new",
                "&eNew bounds: {bounds}", "bounds", bounds.describe());
        msg(sender, "admin-protect.resize-confirm",
                "&eConfirm with /land admin protect resize confirm {region}, or wait 2 minutes to cancel.",
                "region", region.getName());
        return true;
    }

    private boolean confirmResize(CommandSender sender, String[] args) {
        if (args.length != 5) {
            msg(sender, "admin-protect.resize-confirm-usage",
                    "&eUsage: /land admin protect resize confirm <name>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[4]);
        if (region == null) {
            return true;
        }
        PendingResize pending =
                pendingResizes.remove(pendingKey(sender, region.getId()));
        if (pending == null
                || pending.expiresAt < System.currentTimeMillis()
                || !pending.actor.equalsIgnoreCase(sender.getName())) {
            msg(sender, "admin-protect.resize-expired",
                    "&cNo pending resize confirmation for that region.");
            return true;
        }
        AdminProtectedRegion overlapping =
                plugin.getAdminRegions().findOverlapping(
                        pending.bounds.world, pending.bounds.areas,
                        pending.bounds.minY, pending.bounds.maxY,
                        pending.bounds.threeDimensional, region);
        if (overlapping != null) {
            msg(sender, "admin-protect.overlap",
                    "&cProtected region overlaps {region}.",
                    "region", overlapping.getName());
            return true;
        }
        region.setBounds(pending.bounds.world,
                pending.bounds.threeDimensional, pending.bounds.minY,
                pending.bounds.maxY, pending.bounds.areas);
        plugin.getAdminRegions().rebuild();
        auditLand(sender, "ADMIN_PROTECT_RESIZE", null, null,
                "Resized protected region " + region.getName()
                        + " to " + pending.bounds.describe());
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-protect.resized",
                "&aUpdated protected region {region}: {bounds}",
                "region", region.getName(),
                "bounds", pending.bounds.describe());
        return true;
    }

    private boolean cancelResize(CommandSender sender, String[] args) {
        if (args.length != 5) {
            msg(sender, "admin-protect.resize-cancel-usage",
                    "&eUsage: /land admin protect resize cancel <name>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[4]);
        if (region == null) {
            return true;
        }
        pendingResizes.remove(pendingKey(sender, region.getId()));
        msg(sender, "admin-protect.resize-cancelled",
                "&eCancelled pending resize for {region}.",
                "region", region.getName());
        return true;
    }

    private boolean whitelist(CommandSender sender, String[] args) {
        if (args.length < 5) {
            msg(sender, "admin-protect.whitelist-usage",
                    "&eUsage: /land admin protect whitelist add|remove|list <region> [player] [permissions]");
            return true;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        AdminProtectedRegion region = resolveRegion(sender, args[4]);
        if (region == null) {
            return true;
        }
        if ("list".equals(action)) {
            showWhitelist(sender, region);
            return true;
        }
        if (args.length < 6
                || (!"add".equals(action) && !"remove".equals(action))) {
            msg(sender, "admin-protect.whitelist-usage",
                    "&eUsage: /land admin protect whitelist add|remove|list <region> [player] [permissions]");
            return true;
        }
        String playerName = args[5];
        if (!Text.validName(playerName, 2, 16)) {
            msg(sender, "common.invalid-player-name",
                    "&cPlayer names may only contain letters, numbers, underscores, and hyphens.");
            return true;
        }
        if ("remove".equals(action)) {
            region.removeWhitelist(playerName);
            msg(sender, "admin-protect.whitelist-removed",
                    "&aRemoved {player} from the whitelist for {region}.",
                    "player", playerName, "region", region.getName());
        } else {
            EnumSet<AdminRegionPermission> permissions =
                    parsePermissions(args, 6);
            region.setWhitelistPermissions(playerName, permissions);
            msg(sender, "admin-protect.whitelist-added",
                    "&aAdded {player} to the whitelist for {region}: {permissions}",
                    "player", playerName, "region", region.getName(),
                    "permissions", permissionText(permissions));
        }
        auditLand(sender, "ADMIN_PROTECT_WHITELIST_" + action.toUpperCase(Locale.ROOT),
                playerName, null, region.getName());
        plugin.requestSaveAsync(true);
        return true;
    }

    private boolean flag(CommandSender sender, String[] args) {
        if (args.length != 6) {
            msg(sender, "admin-protect.flag-usage",
                    "&eUsage: /land admin protect flag <region> <flag> <allow|deny|default>");
            return true;
        }
        AdminProtectedRegion region = resolveRegion(sender, args[3]);
        if (region == null) {
            return true;
        }
        LandFlag flag = LandFlag.parse(args[4]);
        if (flag == null) {
            msg(sender, "admin-protect.flag-invalid",
                    "&cUnknown flag: {flag}", "flag", args[4]);
            return true;
        }
        String state = args[5].toLowerCase(Locale.ROOT);
        Boolean value = "default".equals(state) ? null
                : ("allow".equals(state) ? Boolean.TRUE
                : ("deny".equals(state) ? Boolean.FALSE : null));
        if (!"default".equals(state) && value == null) {
            msg(sender, "admin-protect.flag-invalid-state",
                    "&cState must be allow, deny, or default.");
            return true;
        }
        region.setFlagOverride(flag, value);
        auditLand(sender, "ADMIN_PROTECT_FLAG", null, null,
                region.getName() + " " + flag.getId() + "=" + state);
        plugin.requestSaveAsync(true);
        refreshMaps();
        msg(sender, "admin-protect.flag-set",
                "&a{region}: {flag} is now {state}.",
                "region", region.getName(), "flag", flag.getId(),
                "state", state);
        return true;
    }

    private void showWhitelist(CommandSender sender,
                               AdminProtectedRegion region) {
        Map<String, EnumSet<AdminRegionPermission>> entries =
                region.getWhitelist();
        msg(sender, "admin-protect.whitelist-header",
                "&6Whitelist for {region}: {count}",
                "region", region.getName(),
                "count", Integer.toString(entries.size()));
        for (Map.Entry<String, EnumSet<AdminRegionPermission>> entry
                : entries.entrySet()) {
            msg(sender, "admin-protect.whitelist-entry",
                    "&e{player} &7[{permissions}]",
                    "player", entry.getKey(),
                    "permissions", permissionText(entry.getValue()));
        }
    }

    private Bounds parseBounds(CommandSender sender, String[] args,
                               int modeIndex) {
        String mode = args[modeIndex].toLowerCase(Locale.ROOT);
        boolean threeD = "3d".equals(mode);
        if (!threeD && !"2d".equals(mode)) {
            msg(sender, "admin-protect.invalid-mode",
                    "&cMode must be 2d or 3d.");
            return null;
        }
        int required = modeIndex + 1 + (threeD ? 6 : 4);
        if (args.length < required) {
            msg(sender, "admin-protect.coordinate-usage",
                    "&cInsufficient coordinates.");
            return null;
        }
        String worldName = args.length > required
                ? args[required] : sender instanceof Player
                ? ((Player) sender).getWorld().getName() : null;
        if (worldName == null) {
            msg(sender, "admin-protect.world-required",
                    "&cWorld is required when using the console.");
            return null;
        }
        World world = Bukkit.getServer().getWorld(worldName);
        if (world == null) {
            for (World candidate : Bukkit.getServer().getWorlds()) {
                if (candidate.getName().equalsIgnoreCase(worldName)) {
                    world = candidate;
                    break;
                }
            }
        }
        if (world == null) {
            msg(sender, "admin-protect.world-missing",
                    "&cWorld not found: {world}", "world", worldName);
            return null;
        }
        try {
            int index = modeIndex + 1;
            int x1 = Integer.parseInt(args[index++]);
            int y1 = threeD ? Integer.parseInt(args[index++]) : 0;
            int z1 = Integer.parseInt(args[index++]);
            int x2 = Integer.parseInt(args[index++]);
            int y2 = threeD ? Integer.parseInt(args[index++]) : world.getMaxHeight() - 1;
            int z2 = Integer.parseInt(args[index]);
            int maxY = world.getMaxHeight() - 1;
            if (threeD && (y1 < 0 || y1 > maxY
                    || y2 < 0 || y2 > maxY)) {
                msg(sender, "admin-protect.invalid-height",
                        "&cY coordinates must be between 0 and {max}.",
                        "max", Integer.toString(maxY));
                return null;
            }
            List<Rect2i> areas = new ArrayList<Rect2i>();
            areas.add(new Rect2i(x1, z1, x2, z2));
            return new Bounds(world.getName(), threeD, y1, y2, areas);
        } catch (NumberFormatException exception) {
            msg(sender, "admin-protect.invalid-coordinate",
                    "&cCoordinates must be integers.");
            return null;
        }
    }

    private EnumSet<AdminRegionPermission> parsePermissions(String[] args,
                                                            int start) {
        if (args.length <= start) {
            return EnumSet.copyOf(AdminRegionPermission.defaults());
        }
        EnumSet<AdminRegionPermission> result =
                EnumSet.noneOf(AdminRegionPermission.class);
        for (int index = start; index < args.length; index++) {
            for (String token : args[index].split(",")) {
                if ("all".equalsIgnoreCase(token)) {
                    result.addAll(AdminRegionPermission.defaults());
                    continue;
                }
                AdminRegionPermission permission =
                        AdminRegionPermission.parse(token);
                if (permission != null) {
                    result.add(permission);
                }
            }
        }
        return result.isEmpty()
                ? EnumSet.copyOf(AdminRegionPermission.defaults()) : result;
    }

    private String permissionText(EnumSet<AdminRegionPermission> permissions) {
        List<String> values = new ArrayList<String>();
        for (AdminRegionPermission permission : permissions) {
            values.add(permission.getId());
        }
        return String.join(", ", values);
    }

    private AdminProtectedRegion resolveRegion(CommandSender sender,
                                               String reference) {
        AdminProtectedRegion region =
                plugin.getAdminRegions().resolve(reference);
        if (region == null) {
            msg(sender, "admin-protect.not-found",
                    "&cProtected region not found: {region}",
                    "region", reference);
        }
        return region;
    }

    private boolean validName(String name) {
        return Text.validName(name, 2, 32);
    }

    private String describe(AdminProtectedRegion region) {
        Rect2i area = region.firstArea();
        if (area == null) {
            return "none";
        }
        if (region.isThreeDimensional()) {
            return region.getWorld() + " 3D " + area.getMinX() + ","
                    + region.getMinY() + "," + area.getMinZ() + " -> "
                    + area.getMaxX() + "," + region.getMaxY() + ","
                    + area.getMaxZ();
        }
        return region.getWorld() + " 2D " + area.getMinX() + ","
                + area.getMinZ() + " -> " + area.getMaxX() + ","
                + area.getMaxZ();
    }

    private String pendingKey(CommandSender sender, String regionId) {
        return Text.key(sender.getName()) + ":" + regionId;
    }

    private void showUsage(CommandSender sender) {
        msg(sender, "admin-protect.usage",
                "&eUsage: /land admin protect create|list|info|rename|delete|resize|whitelist|flag ...");
    }

    private static final class Bounds {
        private final String world;
        private final boolean threeDimensional;
        private final int minY;
        private final int maxY;
        private final List<Rect2i> areas;

        private Bounds(String world, boolean threeDimensional,
                       int minY, int maxY, List<Rect2i> areas) {
            this.world = world;
            this.threeDimensional = threeDimensional;
            this.minY = Math.min(minY, maxY);
            this.maxY = Math.max(minY, maxY);
            this.areas = areas;
        }

        private String describe() {
            Rect2i area = areas.get(0);
            if (threeDimensional) {
                return world + " 3D " + area.getMinX() + "," + minY + ","
                        + area.getMinZ() + " -> " + area.getMaxX() + ","
                        + maxY + "," + area.getMaxZ();
            }
            return world + " 2D " + area.getMinX() + "," + area.getMinZ()
                    + " -> " + area.getMaxX() + "," + area.getMaxZ();
        }
    }

    private static final class PendingResize {
        private final String actor;
        private final String regionId;
        private final Bounds bounds;
        private final long expiresAt;

        private PendingResize(String actor, String regionId,
                              Bounds bounds, long expiresAt) {
            this.actor = actor;
            this.regionId = regionId;
            this.bounds = bounds;
            this.expiresAt = expiresAt;
        }
    }
}
