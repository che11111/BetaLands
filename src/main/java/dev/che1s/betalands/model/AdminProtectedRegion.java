package dev.che1s.betalands.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.util.Text;

public final class AdminProtectedRegion {
    private final String id;
    private String name;
    private String world;
    private final String createdBy;
    private final long createdAt;
    private boolean threeDimensional;
    private int minY;
    private int maxY;
    private final List<Rect2i> areas;
    private final Map<String, EnumSet<AdminRegionPermission>> whitelist;
    private final EnumMap<LandFlag, Boolean> flags;

    public AdminProtectedRegion(String id, String name, String world,
                                String createdBy, long createdAt,
                                boolean threeDimensional, int minY, int maxY,
                                List<Rect2i> areas) {
        this.id = Text.key(id);
        this.name = name;
        this.world = world;
        this.createdBy = createdBy;
        this.createdAt = createdAt <= 0L
                ? System.currentTimeMillis() : createdAt;
        this.areas = new ArrayList<Rect2i>(areas);
        this.whitelist =
                new LinkedHashMap<String, EnumSet<AdminRegionPermission>>();
        this.flags = new EnumMap<LandFlag, Boolean>(LandFlag.class);
        setBounds(world, threeDimensional, minY, maxY, areas);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isThreeDimensional() {
        return threeDimensional;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public List<Rect2i> getAreas() {
        return Collections.unmodifiableList(areas);
    }

    public Rect2i firstArea() {
        return areas.isEmpty() ? null : areas.get(0);
    }

    public void setBounds(String world, boolean threeDimensional,
                          int minY, int maxY, List<Rect2i> areas) {
        if (world == null || areas == null || areas.isEmpty()) {
            throw new IllegalArgumentException("Region world and area are required.");
        }
        this.world = world;
        this.threeDimensional = threeDimensional;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.areas.clear();
        this.areas.addAll(areas);
    }

    public boolean contains(String worldName, int x, int y, int z) {
        if (world == null || !world.equalsIgnoreCase(worldName)) {
            return false;
        }
        if (threeDimensional && (y < minY || y > maxY)) {
            return false;
        }
        for (Rect2i area : areas) {
            if (area.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean intersects(String worldName, List<Rect2i> otherAreas,
                              int otherMinY, int otherMaxY,
                              boolean otherThreeDimensional) {
        if (world == null || !world.equalsIgnoreCase(worldName)
                || otherAreas == null || otherAreas.isEmpty()) {
            return false;
        }
        if (threeDimensional && otherThreeDimensional
                && (maxY < otherMinY || otherMaxY < minY)) {
            return false;
        }
        for (Rect2i area : areas) {
            for (Rect2i other : otherAreas) {
                if (area.intersects(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean intersects(Land land) {
        return land != null && intersects(land.getWorld(), land.getAreas(),
                land.getMinY(), land.getMaxY(), land.isSubclaim());
    }

    public Map<String, EnumSet<AdminRegionPermission>> getWhitelist() {
        Map<String, EnumSet<AdminRegionPermission>> result =
                new LinkedHashMap<String, EnumSet<AdminRegionPermission>>();
        for (Map.Entry<String, EnumSet<AdminRegionPermission>> entry
                : whitelist.entrySet()) {
            result.put(entry.getKey(), EnumSet.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean isWhitelisted(String playerName) {
        return whitelist.containsKey(Text.key(playerName));
    }

    public boolean hasPermission(String playerName,
                                 AdminRegionPermission permission) {
        EnumSet<AdminRegionPermission> permissions =
                whitelist.get(Text.key(playerName));
        return permissions != null && permissions.contains(permission);
    }

    public void setWhitelistPermissions(String playerName,
                                        Set<AdminRegionPermission> permissions) {
        if (permissions == null) {
            whitelist.remove(Text.key(playerName));
        } else {
            whitelist.put(Text.key(playerName),
                    EnumSet.copyOf(permissions));
        }
    }

    public void removeWhitelist(String playerName) {
        whitelist.remove(Text.key(playerName));
    }

    public Boolean getFlagOverride(LandFlag flag) {
        return flags.get(flag);
    }

    public void setFlagOverride(LandFlag flag, Boolean value) {
        if (value == null) {
            flags.remove(flag);
        } else {
            flags.put(flag, value);
        }
    }

    public Map<LandFlag, Boolean> getFlagOverrides() {
        return new EnumMap<LandFlag, Boolean>(flags);
    }

    public void setFlagOverrides(Map<LandFlag, Boolean> values) {
        flags.clear();
        if (values != null) {
            flags.putAll(values);
        }
    }

    public boolean flag(LandFlag flag, boolean globalDefault) {
        Boolean value = flags.get(flag);
        return value == null ? globalDefault : value.booleanValue();
    }

    public AdminProtectedRegion copyForStorage() {
        AdminProtectedRegion copy = new AdminProtectedRegion(id, name, world,
                createdBy, createdAt, threeDimensional, minY, maxY, areas);
        copy.whitelist.putAll(whitelist);
        copy.flags.putAll(flags);
        return copy;
    }
}
