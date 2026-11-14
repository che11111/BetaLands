package dev.che1s.betalands.integration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandMaintenanceService;
import dev.che1s.betalands.service.LandManager;

final class LandMapInfo {
    private LandMapInfo() {
    }

    static String details(BetaLandsPlugin plugin, Land land) {
        Land main = plugin.getLandManager().getFamilyMain(land);
        double bankBalance = main == null ? 0.0D : main.getBankBalance();
        LevelProfile profile = plugin.getLevels().getProfile(main);
        LandMaintenanceService maintenance = plugin.getMaintenanceService();
        double maintenanceFee = maintenance == null
                ? 0.0D : maintenance.getMaintenanceFee(land);
        int maintenanceIntervalDays = profile.getMaintenanceIntervalDays();
        String rentAccounts = rentAccounts(plugin, land);
        String flags = flagStates(plugin, land);

        StringBuilder result = new StringBuilder();
        result.append("<b>").append(escape(land.getDisplayName())).append("</b><br>");
        appendLine(result, plugin.getMessages().map("description.id",
                "Land ID: {value}", "value", escape(land.getId())));
        appendLine(result, plugin.getMessages().map("description.owner",
                "Owner: {value}", "value", escape(land.getOwner())));
        appendLine(result, plugin.getMessages().map("description.world",
                "World: {value}", "value", escape(land.getWorld())));
        appendLine(result, plugin.getMessages().map("description.chunks",
                "Chunks: {value}", "value", Integer.toString(
                        plugin.getLandManager().getOccupiedChunks(land))));
        appendLine(result, plugin.getMessages().map("description.area",
                "Area: {value} blocks", "value", Long.toString(land.area())));
        if (land.isSubclaim()) {
            appendLine(result, plugin.getMessages().map("description.height",
                    "Height: {min} - {max}",
                    "min", Integer.toString(land.getMinY()),
                    "max", Integer.toString(land.getMaxY())));
            appendLine(result, plugin.getMessages().map("description.type-sub",
                    "Type: 3D subclaim"));
        } else {
            appendLine(result, plugin.getMessages().map("description.type-main",
                    "Type: 2D main land"));
        }
        List<Land> children = land.isSubclaim()
                ? Collections.<Land>emptyList()
                : plugin.getLandManager().getChildren(land.getId());
        appendLine(result, plugin.getMessages().map("description.subclaims",
                "Subclaims: {value}", "value", Integer.toString(children.size())));
        appendLine(result, plugin.getMessages().map("description.subclaim-list",
                "Subclaim names: {value}", "value", childNames(plugin, children)));
        appendLine(result, plugin.getMessages().map("description.level",
                "Level: {value}", "value", escape(profile.getDisplayName())));
        appendLine(result, plugin.getMessages().map("description.level-limits",
                "Limits: main={main}, chunks={chunks}, subclaims/main={sub}, family={family}, joined={joined}",
                "main", Integer.toString(profile.getMaxMainLands()),
                "chunks", Integer.toString(profile.getMaxMainChunks()),
                "sub", Integer.toString(profile.getMaxSubclaimsPerMain()),
                "family", Integer.toString(profile.getMaxFamilyMembers()),
                "joined", Integer.toString(profile.getMaxJoinedLands())));
        appendLine(result, plugin.getMessages().map("description.family-count",
                "Family members: {value}/{limit}",
                "value", Integer.toString(plugin.getLandManager().getFamilyMemberCount(land)),
                "limit", Integer.toString(profile.getMaxFamilyMembers())));
        appendLine(result, plugin.getMessages().map("description.bank",
                "Bank: {value}", "value", formatAmount(bankBalance)));
        appendLine(result, main != null && main.isMaintenanceExempt()
                ? plugin.getMessages().map("description.maintenance-exempt",
                "Maintenance: Exempt")
                : plugin.getMessages().map("description.maintenance-interval",
                plugin.getMessages().isMapChinese()
                        ? "领地维护费：{value}/{days}天"
                        : "Land maintenance: {value}/{days} days",
                "value", formatAmount(maintenanceFee),
                "days", Integer.toString(maintenanceIntervalDays)));
        if (main != null && main.getBankBalance() < 0.0D) {
            appendLine(result, plugin.getMessages().map("description.maintenance-debt",
                    "Land bank debt: {value}",
                    "value", formatAmount(-main.getBankBalance())));
        }
        appendLine(result, plugin.getMessages().map("description.next-maintenance",
                "Next maintenance: {value}", "value", maintenance == null
                        ? none(plugin) : formatDate(maintenance.getNextDueAt(land))));
        appendLine(result, plugin.getMessages().map("description.last-maintenance",
                "Last maintenance: {value}",
                "value", formatDate(main == null
                        ? land.getLastMaintenanceAt()
                        : main.getLastMaintenanceAt())));
        appendLine(result, land.isRentEnabled()
                ? plugin.getMessages().map("description.rent-enabled",
                "Rent: {value}/day per player", "value", formatAmount(land.getRentAmount()))
                : plugin.getMessages().map("description.rent-disabled", "Rent: Disabled"));
        if (land.isRentEnabled()) {
            appendLine(result, plugin.getMessages().map("description.next-rent",
                    "Next rent: {value}", "value", maintenance == null
                            ? none(plugin) : formatDate(maintenance.getNextRentAt(land))));
            appendLine(result, plugin.getMessages().map("description.last-rent",
                    "Last rent: {value}",
                    "value", formatDate(land.getLastRentAt())));
        }
        appendLine(result, plugin.getMessages().map("description.rent-accounts",
                "Rent accounts: {value}", "value", rentAccounts));
        appendLine(result, plugin.getMessages().map("description.flags",
                "Protection flags: {value}", "value", flags));
        appendLine(result, plugin.getMessages().map("description.home",
                "Spawn: {value}", "value", home(plugin, land.getHome())));
        appendLine(result, plugin.getMessages().map("description.created",
                "Created: {value}", "value", formatDate(land.getCreatedAt())));
        appendLine(result, plugin.getMessages().map("description.source",
                "Source: {value}", "value", escape(land.getSource())));
        appendLine(result, plugin.getMessages().map("description.inherit",
                "Inherits parent permissions: {value}",
                "value", yesNo(plugin, land.inheritsParentPermissions())));
        appendLine(result, plugin.getMessages().map("description.member-list-header-v3",
                plugin.getMessages().isMapChinese()
                        ? "领地成员列表及权限：" : "Land members and permissions:"));
        appendMemberList(result, plugin, land);
        appendLine(result, plugin.getMessages().map("description.member-list-note-v3",
                plugin.getMessages().isMapChinese()
                        ? "注：未在括号内注明权限的，视为拥有build、break、use、container、rent权限"
                        : "Note: players without permissions in parentheses have build, break, use, "
                                + "container, and rent permissions."));
        return result.toString();
    }

    private static String childNames(BetaLandsPlugin plugin, List<Land> children) {
        List<String> names = new ArrayList<String>();
        for (Land child : children) {
            names.add(child.getDisplayName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names.isEmpty() ? none(plugin) : escape(String.join(", ", names));
    }

    private static String rentAccounts(BetaLandsPlugin plugin, Land land) {
        List<RentAccount> accounts = new ArrayList<RentAccount>();
        Set<String> keys = new HashSet<String>(
                plugin.getLandManager().getRentPayers(land));
        for (Map.Entry<String, RentAccount> entry
                : plugin.getLandManager().getRentAccounts().entrySet()) {
            if (land.getId().equals(entry.getValue().getDebtLandId())) {
                keys.add(entry.getKey());
            }
        }
        for (String playerKey : keys) {
            RentAccount account = plugin.getLandManager().getRentAccount(playerKey);
            if (account != null) {
                accounts.add(account);
            }
        }
        if (accounts.isEmpty()) {
            return none(plugin);
        }
        Collections.sort(accounts, new Comparator<RentAccount>() {
            @Override
            public int compare(RentAccount first, RentAccount second) {
                return first.getPlayerName().compareToIgnoreCase(second.getPlayerName());
            }
        });
        List<String> entries = new ArrayList<String>();
        for (RentAccount account : accounts) {
            entries.add(plugin.getMessages().map("description.rent-account",
                    "{player}: {balance}",
                    "player", escape(account.getPlayerName()),
                    "balance", formatAmount(account.getBalance())));
        }
        return String.join(", ", entries);
    }

    private static String flagStates(BetaLandsPlugin plugin, Land land) {
        List<String> states = new ArrayList<String>();
        for (LandFlag flag : LandFlag.values()) {
            boolean allowed = plugin.getLandManager().getEffectiveLandFlag(land, flag);
            states.add(plugin.getMessages().map("description.flag-state-v2",
                    "{flag}={state}",
                    "flag", flag.getId(),
                    "state", plugin.getMessages().map(allowed
                                    ? "description.allow" : "description.deny",
                            allowed ? "allow" : "deny")));
        }
        return String.join(", ", states);
    }

    private static void appendMemberList(StringBuilder result,
                                         BetaLandsPlugin plugin, Land land) {
        List<String> keys = plugin.getLandManager().getFamilyMemberKeys(land);
        List<String> members = new ArrayList<String>();
        EnumSet<LandPermission> defaultPermissions =
                EnumSet.of(LandPermission.BUILD, LandPermission.BREAK,
                        LandPermission.USE, LandPermission.CONTAINER,
                        LandPermission.RENT);
        for (String key : keys) {
            EnumSet<LandPermission> permissions =
                    EnumSet.noneOf(LandPermission.class);
            for (LandPermission permission : LandPermission.values()) {
                if (plugin.getLandManager().hasPermissionInLand(key, land, permission,
                        new HashSet<String>())) {
                    permissions.add(permission);
                }
            }
            String name = escape(displayName(plugin, key));
            if (permissions.equals(defaultPermissions)) {
                members.add(name);
            } else {
                String permissionsText = permissions.isEmpty()
                        ? plugin.getMessages().map("description.member-no-permissions-v3",
                        plugin.getMessages().isMapChinese() ? "无权限" : "no permissions")
                        : permissionNames(plugin, permissions);
                members.add(plugin.getMessages().map("description.member-permission-inline-v3",
                        plugin.getMessages().isMapChinese()
                                ? "{player}（{permissions}）" : "{player} ({permissions})",
                        "player", name,
                        "permissions", permissionsText));
            }
        }
        appendLine(result, members.isEmpty() ? none(plugin) : String.join(
                plugin.getMessages().map("description.member-separator",
                        plugin.getMessages().isMapChinese() ? "、" : ", "), members));
    }

    static List<PermissionGroup> groupPermissions(
            Map<String, EnumSet<LandPermission>> permissionsByPlayer) {
        Map<String, PermissionGroup> groups = new LinkedHashMap<String, PermissionGroup>();
        for (Map.Entry<String, EnumSet<LandPermission>> entry
                : permissionsByPlayer.entrySet()) {
            EnumSet<LandPermission> permissions = entry.getValue() == null
                    ? EnumSet.noneOf(LandPermission.class) : entry.getValue();
            String key = permissionKey(permissions);
            PermissionGroup group = groups.get(key);
            if (group == null) {
                group = new PermissionGroup(EnumSet.copyOf(permissions));
                groups.put(key, group);
            }
            group.players.add(entry.getKey());
        }
        return new ArrayList<PermissionGroup>(groups.values());
    }

    static PermissionGroup chooseSharedGroup(List<PermissionGroup> groups,
                                             Set<LandPermission> preferred) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        if (groups.size() == 1) {
            return groups.get(0).players.size() >= 2 ? groups.get(0) : null;
        }
        PermissionGroup selected = null;
        for (PermissionGroup group : groups) {
            if (selected == null || group.players.size() > selected.players.size()
                    || (group.players.size() == selected.players.size()
                    && preferred != null
                    && group.permissions.equals(preferred)
                    && !selected.permissions.equals(preferred))) {
                selected = group;
            }
        }
        return selected;
    }

    private static String permissionKey(Set<LandPermission> permissions) {
        List<String> values = new ArrayList<String>();
        for (LandPermission permission : LandPermission.values()) {
            if (permissions.contains(permission)) {
                values.add(permission.getId());
            }
        }
        return String.join(",", values);
    }

    private static String permissionNames(BetaLandsPlugin plugin,
                                          Set<LandPermission> permissions) {
        List<String> names = new ArrayList<String>();
        for (LandPermission permission : LandPermission.values()) {
            if (permissions.contains(permission)) {
                names.add(permission.getId());
            }
        }
        return names.isEmpty() ? none(plugin) : String.join(
                plugin.getMessages().map("description.member-separator",
                        plugin.getMessages().isMapChinese() ? "、" : ", "), names);
    }

    static final class PermissionGroup {
        private final EnumSet<LandPermission> permissions;
        private final List<String> players = new ArrayList<String>();

        private PermissionGroup(EnumSet<LandPermission> permissions) {
            this.permissions = permissions;
        }

        EnumSet<LandPermission> getPermissions() {
            return permissions;
        }

        List<String> getPlayers() {
            return players;
        }
    }

    private static String displayName(BetaLandsPlugin plugin, String key) {
        PlayerData data = plugin.getLandManager().getPlayerData(key);
        return data == null || data.getLastName() == null
                || data.getLastName().length() == 0 ? key : data.getLastName();
    }

    private static String home(BetaLandsPlugin plugin, HomePoint home) {
        if (home == null) {
            return none(plugin);
        }
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f",
                home.getX(), home.getY(), home.getZ());
    }

    private static String yesNo(BetaLandsPlugin plugin, boolean value) {
        return plugin.getMessages().map(value
                        ? "description.yes" : "description.no",
                value ? "Yes" : "No");
    }

    private static String none(BetaLandsPlugin plugin) {
        return plugin.getMessages().map("description.none", "None");
    }

    private static String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(time));
    }

    static int occupiedChunks(List<Rect2i> areas) {
        return LandManager.countOccupiedChunks(areas);
    }

    private static String formatAmount(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private static void appendLine(StringBuilder result, String line) {
        result.append(line).append("<br>");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
