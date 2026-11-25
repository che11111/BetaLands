package dev.che1s.betalands.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdminRegionInfo {
    private final String id;
    private final String name;
    private final String world;
    private final boolean threeDimensional;
    private final int minY;
    private final int maxY;
    private final String createdBy;
    private final long createdAt;
    private final List<AreaInfo> areas;
    private final Map<String, Set<AdminRegionPermission>> whitelist;
    private final Map<LandFlag, Boolean> flags;

    AdminRegionInfo(String id, String name, String world,
                    boolean threeDimensional, int minY, int maxY,
                    String createdBy, long createdAt, List<AreaInfo> areas,
                    Map<String, Set<AdminRegionPermission>> whitelist,
                    Map<LandFlag, Boolean> flags) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.threeDimensional = threeDimensional;
        this.minY = minY;
        this.maxY = maxY;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.areas = Collections.unmodifiableList(
                new ArrayList<AreaInfo>(areas));
        Map<String, Set<AdminRegionPermission>> memberCopy =
                new LinkedHashMap<String, Set<AdminRegionPermission>>();
        for (Map.Entry<String, Set<AdminRegionPermission>> entry
                : whitelist.entrySet()) {
            memberCopy.put(entry.getKey(), Collections.unmodifiableSet(
                    EnumSet.copyOf(entry.getValue())));
        }
        this.whitelist = Collections.unmodifiableMap(memberCopy);
        this.flags = Collections.unmodifiableMap(
                new EnumMap<LandFlag, Boolean>(flags));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getWorld() { return world; }
    public boolean isThreeDimensional() { return threeDimensional; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public String getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public List<AreaInfo> getAreas() { return areas; }
    public Map<String, Set<AdminRegionPermission>> getWhitelist() {
        return whitelist;
    }
    public int getWhitelistSize() { return whitelist.size(); }
    public Map<LandFlag, Boolean> getFlags() { return flags; }

    public static final class AreaInfo {
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        AreaInfo(int minX, int minZ, int maxX, int maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public int getMinX() { return minX; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxZ() { return maxZ; }
    }
}
