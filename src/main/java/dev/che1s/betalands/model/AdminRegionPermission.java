package dev.che1s.betalands.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum AdminRegionPermission {
    LANDCREATE("landcreate"),
    LANDCLAIM("landclaim"),
    BUILD("build"),
    BREAK("break"),
    USE("use"),
    CONTAINER("container"),
    PVP("pvp");

    private final String id;

    AdminRegionPermission(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static AdminRegionPermission parse(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (AdminRegionPermission permission : values()) {
            if (permission.id.equals(key)) {
                return permission;
            }
        }
        if ("create".equals(key)) {
            return LANDCREATE;
        }
        if ("claim".equals(key)) {
            return LANDCLAIM;
        }
        if ("place".equals(key)) {
            return BUILD;
        }
        if ("destroy".equals(key)) {
            return BREAK;
        }
        return null;
    }

    public static Set<AdminRegionPermission> defaults() {
        return EnumSet.allOf(AdminRegionPermission.class);
    }
}
