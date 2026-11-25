package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;

import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.util.Text;

public final class AdminRegionManager {
    private final Map<String, AdminProtectedRegion> regions =
            new LinkedHashMap<String, AdminProtectedRegion>();
    private final Map<String, String> nameIndex = new HashMap<String, String>();
    private final Map<String, List<AdminProtectedRegion>> chunkIndex =
            new HashMap<String, List<AdminProtectedRegion>>();

    public void clear() {
        regions.clear();
        nameIndex.clear();
        chunkIndex.clear();
    }

    public void addLoadedRegion(AdminProtectedRegion region) {
        if (region == null || regions.containsKey(region.getId())) {
            return;
        }
        regions.put(region.getId(), region);
        index(region);
    }

    public void addRegion(AdminProtectedRegion region) {
        addLoadedRegion(region);
    }

    public void removeRegion(AdminProtectedRegion region) {
        if (region == null) {
            return;
        }
        regions.remove(region.getId());
        rebuild();
    }

    public void rebuild() {
        nameIndex.clear();
        chunkIndex.clear();
        for (AdminProtectedRegion region : regions.values()) {
            index(region);
        }
    }

    public List<AdminProtectedRegion> getRegions() {
        List<AdminProtectedRegion> result =
                new ArrayList<AdminProtectedRegion>(regions.values());
        Collections.sort(result, new Comparator<AdminProtectedRegion>() {
            @Override
            public int compare(AdminProtectedRegion first,
                               AdminProtectedRegion second) {
                return first.getName().compareToIgnoreCase(second.getName());
            }
        });
        return Collections.unmodifiableList(result);
    }

    public AdminProtectedRegion getById(String id) {
        return id == null ? null : regions.get(Text.key(id));
    }

    public AdminProtectedRegion resolve(String reference) {
        if (reference == null || reference.trim().length() == 0) {
            return null;
        }
        AdminProtectedRegion direct = getById(reference);
        if (direct != null) {
            return direct;
        }
        String id = nameIndex.get(Text.key(reference));
        return id == null ? null : regions.get(id);
    }

    public boolean nameExists(String name, AdminProtectedRegion ignored) {
        String id = nameIndex.get(Text.key(name));
        return id != null && (ignored == null
                || !id.equals(ignored.getId()));
    }

    public AdminProtectedRegion findAt(Location location) {
        return location == null || location.getWorld() == null ? null
                : findAt(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    public AdminProtectedRegion findAt(String worldName, int x, int y, int z) {
        List<AdminProtectedRegion> candidates = chunkIndex.get(
                chunkKey(worldName, x >> 4, z >> 4));
        if (candidates == null) {
            return null;
        }
        for (AdminProtectedRegion region : candidates) {
            if (region.contains(worldName, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    public List<AdminProtectedRegion> findIntersecting(String worldName,
                                                       List<Rect2i> areas,
                                                       int minY, int maxY,
                                                       boolean threeDimensional) {
        List<AdminProtectedRegion> result =
                new ArrayList<AdminProtectedRegion>();
        for (AdminProtectedRegion region : regions.values()) {
            if (region.intersects(worldName, areas, minY, maxY,
                    threeDimensional)) {
                result.add(region);
            }
        }
        return result;
    }

    public AdminProtectedRegion findForLand(Land land) {
        if (land == null) {
            return null;
        }
        for (AdminProtectedRegion region : regions.values()) {
            if (region.intersects(land)) {
                return region;
            }
        }
        return null;
    }

    public AdminProtectedRegion findOverlapping(String worldName,
                                                List<Rect2i> areas,
                                                int minY, int maxY,
                                                boolean threeDimensional,
                                                AdminProtectedRegion ignored) {
        for (AdminProtectedRegion region : regions.values()) {
            if (ignored != null && region.getId().equals(ignored.getId())) {
                continue;
            }
            if (region.intersects(worldName, areas, minY, maxY,
                    threeDimensional)) {
                return region;
            }
        }
        return null;
    }

    private void index(AdminProtectedRegion region) {
        nameIndex.put(Text.key(region.getName()), region.getId());
        for (Rect2i area : region.getAreas()) {
            int minChunkX = area.getMinX() >> 4;
            int maxChunkX = area.getMaxX() >> 4;
            int minChunkZ = area.getMinZ() >> 4;
            int maxChunkZ = area.getMaxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    String key = chunkKey(region.getWorld(), chunkX, chunkZ);
                    List<AdminProtectedRegion> values = chunkIndex.get(key);
                    if (values == null) {
                        values = new ArrayList<AdminProtectedRegion>();
                        chunkIndex.put(key, values);
                    }
                    values.add(region);
                }
            }
        }
    }

    private String chunkKey(String worldName, int chunkX, int chunkZ) {
        return Text.key(worldName) + ":" + chunkX + ":" + chunkZ;
    }
}
