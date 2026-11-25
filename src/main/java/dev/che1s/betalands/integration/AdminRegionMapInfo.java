package dev.che1s.betalands.integration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.AdminRegionPermission;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.Rect2i;

final class AdminRegionMapInfo {
    private AdminRegionMapInfo() {
    }

    static String details(BetaLandsPlugin plugin, AdminProtectedRegion region) {
        boolean chinese = plugin.getMessages().isMapChinese();
        Rect2i area = region.firstArea();
        String bounds = area == null ? "None" : area.getMinX() + ", "
                + (region.isThreeDimensional() ? region.getMinY() + ", " : "")
                + area.getMinZ() + " -> " + area.getMaxX() + ", "
                + (region.isThreeDimensional() ? region.getMaxY() + ", " : "")
                + area.getMaxZ();
        StringBuilder result = new StringBuilder();
        result.append("<b>").append(escape(region.getName())).append("</b><br>");
        line(result, plugin, "admin-region.id",
                chinese ? "\u533a\u57df ID\uff1a{value}"
                        : "Region ID: {value}",
                "value", region.getId());
        line(result, plugin, "admin-region.type",
                chinese ? "\u7c7b\u578b\uff1a{value}" : "Type: {value}",
                "value", region.isThreeDimensional()
                        ? (chinese ? "3D \u7acb\u4f53\u533a\u57df"
                        : "3D volume") : (chinese ? "2D \u5168\u9ad8\u533a\u57df"
                        : "2D full-height"));
        line(result, plugin, "admin-region.world",
                chinese ? "\u4e16\u754c\uff1a{value}" : "World: {value}",
                "value", region.getWorld());
        line(result, plugin, "admin-region.bounds",
                chinese ? "\u8fb9\u754c\uff1a{value}" : "Bounds: {value}",
                "value", bounds);
        line(result, plugin, "admin-region.whitelist",
                chinese ? "\u767d\u540d\u5355\u4eba\u6570\uff1a{value}"
                        : "Whitelist members: {value}",
                "value", Integer.toString(region.getWhitelist().size()));
        appendWhitelist(result, plugin, region, chinese);
        appendNonWhitelistPermissions(result, plugin, region, chinese);
        line(result, plugin, "admin-region.created",
                chinese ? "\u521b\u5efa\u65f6\u95f4\uff1a{value}"
                        : "Created: {value}",
                "value", new SimpleDateFormat("yyyy-MM-dd HH:mm")
                        .format(new Date(region.getCreatedAt())));
        return result.toString();
    }

    private static void appendWhitelist(StringBuilder result,
                                        BetaLandsPlugin plugin,
                                        AdminProtectedRegion region,
                                        boolean chinese) {
        line(result, plugin, "admin-region.whitelist-members",
                chinese ? "\u767d\u540d\u5355\u6210\u5458\u53ca\u6743\u9650\uff1a"
                        : "Whitelist members and permissions:");
        List<String> entries = new ArrayList<String>();
        EnumSet<AdminRegionPermission> defaults =
                EnumSet.copyOf(AdminRegionPermission.defaults());
        for (Map.Entry<String, EnumSet<AdminRegionPermission>> entry
                : region.getWhitelist().entrySet()) {
            EnumSet<AdminRegionPermission> permissions =
                    EnumSet.copyOf(entry.getValue());
            String name = escape(entry.getKey());
            if (permissions.equals(defaults)) {
                entries.add(name);
            } else if (permissions.isEmpty()) {
                entries.add(name + (chinese
                        ? "\uff08\u65e0\u6743\u9650\uff09"
                        : " (no permissions)"));
            } else {
                entries.add(name + (chinese ? "\uff08" : " (")
                        + permissionNames(permissions)
                        + (chinese ? "\uff09" : ")"));
            }
        }
        appendLine(result, entries.isEmpty()
                ? (chinese ? "\u65e0" : "None")
                : String.join(chinese ? "\u3001" : ", ", entries));
        line(result, plugin, "admin-region.whitelist-note",
                chinese ? "\u6ce8\uff1a\u672a\u5728\u62ec\u53f7\u5185\u6ce8\u660e\u89c6\u4e3a\u62e5\u6709 \u201clandcreate, landclaim, build, break, use, container, pvp\u201d \u6743\u9650\u3002"
                        : "Note: players without permissions in parentheses have landcreate, landclaim, build, break, use, container, and pvp permissions.");
    }

    private static void appendNonWhitelistPermissions(
            StringBuilder result, BetaLandsPlugin plugin,
            AdminProtectedRegion region, boolean chinese) {
        boolean pvp = plugin.getAdminRegionProtection().flag(
                region, LandFlag.PVP);
        String value = chinese
                ? "\u5708\u5730=\u4e0d\u80fd\uff0c\u6269\u5c55\u9886\u5730=\u4e0d\u80fd\uff0c"
                + "\u653e\u7f6e=\u4e0d\u80fd\uff0c\u7834\u574f=\u4e0d\u80fd\uff0c"
                + "\u4ea4\u4e92=\u4e0d\u80fd\uff0c\u5bb9\u5668=\u4e0d\u80fd\uff0cPVP="
                + (pvp ? "\u80fd" : "\u4e0d\u80fd")
                : "landcreate=deny, landclaim=deny, build=deny, "
                + "break=deny, use=deny, container=deny, pvp="
                + (pvp ? "allow" : "deny");
        line(result, plugin, "admin-region.non-whitelist",
                chinese ? "\u975e\u767d\u540d\u5355\u6743\u9650\uff1a{value}"
                        : "Non-whitelist permissions: {value}",
                "value", value);
    }

    private static String permissionNames(
            EnumSet<AdminRegionPermission> permissions) {
        List<String> values = new ArrayList<String>();
        for (AdminRegionPermission permission : permissions) {
            values.add(permission.getId());
        }
        return String.join(", ", values);
    }

    static String markerSetLabel(BetaLandsPlugin plugin) {
        return plugin.getMessages().map("admin-region.marker-set",
                "\u533a\u57df\u4fdd\u62a4");
    }

    private static void line(StringBuilder result, BetaLandsPlugin plugin,
                             String key, String fallback,
                             String... replacements) {
        result.append(plugin.getMessages().map(key, fallback, replacements))
                .append("<br>");
    }

    private static void appendLine(StringBuilder result, String line) {
        result.append(line).append("<br>");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
