package dev.che1s.betalands.model;

import java.util.Locale;

public enum LandFlag {
    EXPLOSION("explosion", "Explosions"),
    FIRE("fire", "Fire"),
    FLUID("fluid", "Fluid crossing"),
    PVP("pvp", "PVP"),
    TP("tp", "Public teleport"),
    MOBS("mobs", "Natural mob spawning"),
    TRAMPLING("trampling", "Trampling");

    private final String id;
    private final String displayName;

    LandFlag(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static LandFlag parse(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (LandFlag flag : values()) {
            if (flag.id.equals(key)) {
                return flag;
            }
        }
        if ("explosions".equals(key)) {
            return EXPLOSION;
        }
        if ("fluids".equals(key)) {
            return FLUID;
        }
        if ("mob".equals(key) || "spawning".equals(key)) {
            return MOBS;
        }
        if ("trample".equals(key)) {
            return TRAMPLING;
        }
        return null;
    }
}
