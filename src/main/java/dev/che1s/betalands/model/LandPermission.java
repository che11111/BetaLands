package dev.che1s.betalands.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum LandPermission {
    BUILD("build"),
    BREAK("break"),
    USE("use"),
    CONTAINER("container"),
    RENT("rent"),
    PVP("pvp"),
    MANAGE("manage");

    private final String id;

    LandPermission(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static LandPermission parse(String value) {
        String key = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (LandPermission permission : values()) {
            if (permission.id.equals(key)) {
                return permission;
            }
        }
        if ("place".equals(key)) {
            return BUILD;
        }
        if ("destroy".equals(key)) {
            return BREAK;
        }
        if ("chest".equals(key)) {
            return CONTAINER;
        }
        return null;
    }

    public static Set<LandPermission> defaultsForMember(boolean pvp) {
        EnumSet<LandPermission> permissions = EnumSet.of(
                BUILD, BREAK, USE, CONTAINER, RENT);
        if (pvp) {
            permissions.add(PVP);
        }
        return permissions;
    }

    public static Set<LandPermission> allGrantable(boolean pvp) {
        EnumSet<LandPermission> permissions = EnumSet.allOf(LandPermission.class);
        if (!pvp) {
            permissions.remove(PVP);
        }
        return permissions;
    }
}
