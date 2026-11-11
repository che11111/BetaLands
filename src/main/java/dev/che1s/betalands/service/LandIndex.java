package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.util.Text;

/**
 * Owns the in-memory land catalog and its lookup indexes.
 */
final class LandIndex {
    private final Map<String, Land> lands = new LinkedHashMap<String, Land>();
    private final Map<String, Land> deletedLands =
            new LinkedHashMap<String, Land>();
    private final Map<String, List<Land>> chunkIndex =
            new HashMap<String, List<Land>>();
    private final Map<String, List<Land>> childrenIndex =
            new HashMap<String, List<Land>>();
    private final Map<String, List<Land>> ownerMainIndex =
            new HashMap<String, List<Land>>();
    private final Map<String, List<Land>> ownerLandIndex =
            new HashMap<String, List<Land>>();
    private final Map<String, Set<String>> memberLandIndex =
            new HashMap<String, Set<String>>();
    private final Map<String, Set<String>> landMemberIndex =
            new HashMap<String, Set<String>>();
    private final Map<String, String> territoryNameIndex =
            new HashMap<String, String>();
    private final Map<String, Integer> occupiedChunksCache =
            new HashMap<String, Integer>();

    void clear() {
        lands.clear();
        deletedLands.clear();
        chunkIndex.clear();
        childrenIndex.clear();
        ownerMainIndex.clear();
        ownerLandIndex.clear();
        memberLandIndex.clear();
        landMemberIndex.clear();
        territoryNameIndex.clear();
        occupiedChunksCache.clear();
    }

    Land get(String id) {
        return id == null ? null : lands.get(Text.key(id));
    }

    Land getIncludingDeleted(String id) {
        Land land = get(id);
        return land == null && id != null
                ? deletedLands.get(Text.key(id)) : land;
    }

    List<Land> getAll() {
        return new ArrayList<Land>(lands.values());
    }

    List<Land> getPersistedLands() {
        List<Land> result = new ArrayList<Land>(lands.values());
        result.addAll(deletedLands.values());
        return result;
    }

    List<Land> getDeletedLands() {
        return new ArrayList<Land>(deletedLands.values());
    }

    List<Land> getOwnedMainLands(String owner) {
        List<Land> indexed = ownerMainIndex.get(Text.key(owner));
        return indexed == null ? new ArrayList<Land>()
                : new ArrayList<Land>(indexed);
    }

    List<Land> getOwnedLands(String owner) {
        List<Land> indexed = ownerLandIndex.get(Text.key(owner));
        return indexed == null ? new ArrayList<Land>()
                : new ArrayList<Land>(indexed);
    }

    List<Land> getChildren(String parentId) {
        List<Land> indexed = childrenIndex.get(Text.key(parentId));
        return indexed == null ? new ArrayList<Land>()
                : new ArrayList<Land>(indexed);
    }

    List<Land> getMemberLands(String playerName) {
        Set<String> indexed = memberLandIndex.get(Text.key(playerName));
        List<Land> result = new ArrayList<Land>();
        if (indexed == null) {
            return result;
        }
        for (String id : indexed) {
            Land land = lands.get(id);
            if (land != null) {
                result.add(land);
            }
        }
        return result;
    }

    boolean contains(String id) {
        return id != null && (lands.containsKey(id)
                || deletedLands.containsKey(id));
    }

    void add(Land land) {
        if (land == null || land.getId() == null || land.getId().length() == 0) {
            throw new IllegalArgumentException("Land id is required");
        }
        if (contains(land.getId())) {
            throw new IllegalArgumentException("Duplicate land id: " + land.getId());
        }
        if (land.isDeleted()) {
            Land parent = getIncludingDeleted(land.getParentId());
            if (parent != null) {
                land.setParentDisplayName(parent.getName());
            }
            deletedLands.put(land.getId(), land);
            return;
        }
        if (land.isSubclaim()) {
            Land parent = lands.get(land.getParentId());
            if (parent != null) {
                land.setParentDisplayName(parent.getName());
            }
        }
        lands.put(land.getId(), land);
        indexLand(land);
        indexRelationships(land);
        indexMembership(land);
        if (land.isSubclaim()) {
            Land parent = lands.get(land.getParentId());
            if (parent != null) {
                land.setParentDisplayName(parent.getName());
            }
        } else {
            for (Land child : getChildren(land.getId())) {
                child.setParentDisplayName(land.getName());
                territoryNameIndex.put(
                        Text.key(child.getTerritoryName()), child.getId());
            }
        }
        for (Land archived : deletedLands.values()) {
            if (land.getId().equals(archived.getParentId())) {
                archived.setParentDisplayName(land.getName());
            }
        }
        territoryNameIndex.put(Text.key(land.getTerritoryName()), land.getId());
    }

    void remove(String id) {
        if (id != null) {
            Land removed = lands.remove(id);
            removeMembership(removed);
        }
    }

    void archive(Land land) {
        if (land == null) {
            return;
        }
        Land removed = lands.remove(land.getId());
        removeMembership(removed == null ? land : removed);
        deletedLands.put(land.getId(), land);
    }

    void reindexAfterMutation() {
        chunkIndex.clear();
        childrenIndex.clear();
        ownerMainIndex.clear();
        ownerLandIndex.clear();
        memberLandIndex.clear();
        landMemberIndex.clear();
        for (Land land : lands.values()) {
            indexLand(land);
            indexRelationships(land);
            indexMembership(land);
        }
        linkParentDisplayNames();
        rebuildTerritoryNameIndex();
    }

    void indexClaimedChunk(Land land, int chunkX, int chunkZ) {
        indexChunkCell(land, chunkKey(land.getWorld(), chunkX, chunkZ));
    }

    void refreshMembership(Land land) {
        if (land == null || lands.get(land.getId()) != land) {
            return;
        }
        removeMembership(land);
        indexMembership(land);
    }

    Land findLandAt(String worldName, int x, int y, int z) {
        List<Land> indexed = chunkIndex.get(
                chunkKey(worldName, x >> 4, z >> 4));
        if (indexed == null || indexed.isEmpty()) {
            return null;
        }
        List<Land> candidates = new ArrayList<Land>();
        for (Land land : indexed) {
            if (land.contains(worldName, x, y, z)) {
                candidates.add(land);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates, new Comparator<Land>() {
            @Override
            public int compare(Land first, Land second) {
                if (first.isSubclaim() != second.isSubclaim()) {
                    return first.isSubclaim() ? -1 : 1;
                }
                int firstHeight = first.getMaxY() - first.getMinY();
                int secondHeight = second.getMaxY() - second.getMinY();
                if (firstHeight != secondHeight) {
                    return firstHeight < secondHeight ? -1 : 1;
                }
                long firstArea = first.area();
                long secondArea = second.area();
                if (firstArea != secondArea) {
                    return firstArea < secondArea ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return candidates.get(0);
    }

    Land resolveRegion(String reference) {
        if (reference == null || reference.length() == 0) {
            return null;
        }
        String ref = Text.key(reference);
        Land exact = lands.get(ref);
        if (exact != null) {
            return exact;
        }
        String namedId = territoryNameIndex.get(ref);
        if (namedId != null) {
            return lands.get(namedId);
        }
        for (Land land : lands.values()) {
            if (land.getTerritoryName().equalsIgnoreCase(reference)) {
                return land;
            }
            if ((Text.key(land.getOwner()) + ":"
                    + Text.key(land.getTerritoryName())).equalsIgnoreCase(ref)) {
                return land;
            }
        }
        return null;
    }

    Land findByName(String name) {
        if (name == null || name.trim().length() == 0) {
            return null;
        }
        String id = territoryNameIndex.get(Text.key(name));
        return id == null ? null : lands.get(id);
    }

    Land findMainAtChunk(String worldName, int chunkX, int chunkZ) {
        List<Land> indexed = chunkIndex.get(chunkKey(worldName, chunkX, chunkZ));
        if (indexed == null) {
            return null;
        }
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        for (Land land : indexed) {
            if (!land.isSubclaim() && land.containsXZ(worldName, x, z)) {
                return land;
            }
        }
        return null;
    }

    boolean landContainsChunk(Land land, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        return land.containsXZ(land.getWorld(), x, z);
    }

    boolean isChunkAdjacentToLand(Land land, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        for (int offset = 0; offset < 16; offset++) {
            if (land.containsXZ(land.getWorld(), minX - 1, minZ + offset)
                    || land.containsXZ(land.getWorld(), maxX + 1, minZ + offset)
                    || land.containsXZ(land.getWorld(), minX + offset, minZ - 1)
                    || land.containsXZ(land.getWorld(), minX + offset, maxZ + 1)) {
                return true;
            }
        }
        return false;
    }

    int countMainLands(String owner) {
        List<Land> indexed = ownerMainIndex.get(Text.key(owner));
        return indexed == null ? 0 : indexed.size();
    }

    int getOccupiedChunks(Land land) {
        if (land == null) {
            return 0;
        }
        String cacheKey = land.getId() + ":" + land.getGeometryRevision();
        Integer cached = occupiedChunksCache.get(cacheKey);
        if (cached != null) {
            return cached.intValue();
        }
        int count = ClaimService.countOccupiedChunks(land.getAreas());
        rememberCache(occupiedChunksCache, cacheKey, Integer.valueOf(count));
        return count;
    }

    private void indexLand(Land land) {
        Set<String> cells = new HashSet<String>();
        for (Rect2i area : land.getAreas()) {
            int minChunkX = area.getMinX() >> 4;
            int maxChunkX = area.getMaxX() >> 4;
            int minChunkZ = area.getMinZ() >> 4;
            int maxChunkZ = area.getMaxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    cells.add(chunkKey(land.getWorld(), chunkX, chunkZ));
                }
            }
        }
        for (String cell : cells) {
            indexChunkCell(land, cell);
        }
    }

    private void indexChunkCell(Land land, String cell) {
        List<Land> indexed = chunkIndex.get(cell);
        if (indexed == null) {
            indexed = new ArrayList<Land>();
            chunkIndex.put(cell, indexed);
        }
        if (!indexed.contains(land)) {
            indexed.add(land);
        }
    }

    private void indexRelationships(Land land) {
        String ownerKey = Text.key(land.getOwner());
        List<Land> ownedLands = ownerLandIndex.get(ownerKey);
        if (ownedLands == null) {
            ownedLands = new ArrayList<Land>();
            ownerLandIndex.put(ownerKey, ownedLands);
        }
        ownedLands.add(land);
        if (land.isSubclaim()) {
            List<Land> children = childrenIndex.get(land.getParentId());
            if (children == null) {
                children = new ArrayList<Land>();
                childrenIndex.put(land.getParentId(), children);
            }
            children.add(land);
        } else {
            List<Land> owned = ownerMainIndex.get(ownerKey);
            if (owned == null) {
                owned = new ArrayList<Land>();
                ownerMainIndex.put(ownerKey, owned);
            }
            owned.add(land);
        }
    }

    private void indexMembership(Land land) {
        if (land == null) {
            return;
        }
        Set<String> players = new LinkedHashSet<String>();
        for (String playerKey : land.getMembers().keySet()) {
            players.add(playerKey);
            Set<String> ids = memberLandIndex.get(playerKey);
            if (ids == null) {
                ids = new LinkedHashSet<String>();
                memberLandIndex.put(playerKey, ids);
            }
            ids.add(land.getId());
        }
        landMemberIndex.put(land.getId(), players);
    }

    private void removeMembership(Land land) {
        if (land == null) {
            return;
        }
        Set<String> players = landMemberIndex.remove(land.getId());
        if (players == null) {
            return;
        }
        for (String playerKey : players) {
            Set<String> ids = memberLandIndex.get(playerKey);
            if (ids != null) {
                ids.remove(land.getId());
                if (ids.isEmpty()) {
                    memberLandIndex.remove(playerKey);
                }
            }
        }
    }

    private void linkParentDisplayNames() {
        for (Land land : lands.values()) {
            if (land.isSubclaim()) {
                Land parent = lands.get(land.getParentId());
                if (parent != null) {
                    land.setParentDisplayName(parent.getName());
                }
            }
        }
    }

    private void rebuildTerritoryNameIndex() {
        territoryNameIndex.clear();
        for (Land land : lands.values()) {
            String key = Text.key(land.getTerritoryName());
            String previous = territoryNameIndex.put(key, land.getId());
            if (previous != null && !previous.equals(land.getId())) {
                throw new IllegalStateException(
                        "Duplicate land name: " + land.getTerritoryName());
            }
        }
    }

    private String chunkKey(String worldName, int chunkX, int chunkZ) {
        return Text.key(worldName) + ":" + chunkX + ":" + chunkZ;
    }

    private <T> void rememberCache(Map<String, T> cache, String key, T value) {
        if (cache.size() >= 512) {
            cache.clear();
        }
        cache.put(key, value);
    }
}
