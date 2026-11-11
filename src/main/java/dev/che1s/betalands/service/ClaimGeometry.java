package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import dev.che1s.betalands.economy.EconomyService;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

/**
 * Pure geometry operations used by claims and map boundaries.
 */
final class ClaimGeometry {
    private ClaimGeometry() {
    }

    static Rect2i chunkBounds(int blockX, int blockZ) {
        int minX = (blockX >> 4) << 4;
        int minZ = (blockZ >> 4) << 4;
        return new Rect2i(minX, minZ, minX + 15, minZ + 15);
    }

    static int countOccupiedChunks(List<Rect2i> areas) {
        Set<Long> chunks = new HashSet<Long>();
        for (Rect2i area : areas) {
            int minChunkX = area.getMinX() >> 4;
            int maxChunkX = area.getMaxX() >> 4;
            int minChunkZ = area.getMinZ() >> 4;
            int maxChunkZ = area.getMaxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(((long) chunkX << 32)
                            ^ (chunkZ & 0xffffffffL));
                }
            }
        }
        return chunks.size();
    }

    static double calculateClaimPrice(LevelProfile profile, long chunks) {
        return EconomyService.round(profile.getClaimChunkPrice()
                + profile.getClaimChunkIncrement() * Math.max(0L, chunks))
                .doubleValue();
    }

    static boolean exceedsMainChunkLimit(LevelProfile profile,
                                         int currentChunks) {
        return profile == null || currentChunks >= profile.getMaxMainChunks();
    }

    /**
     * Checks rectangle coverage with an X sweep line. The cost depends on the
     * number of parent rectangles, not on the selection width in blocks.
     */
    static boolean isAreaInsideParent(Land parent, Rect2i area) {
        List<Rect2i> clipped = new ArrayList<Rect2i>();
        TreeSet<Integer> xEdges = new TreeSet<Integer>();
        xEdges.add(Integer.valueOf(area.getMinX()));
        xEdges.add(Integer.valueOf(area.getMaxX() + 1));
        for (Rect2i parentArea : parent.getAreas()) {
            int minX = Math.max(area.getMinX(), parentArea.getMinX());
            int maxX = Math.min(area.getMaxX(), parentArea.getMaxX());
            int minZ = Math.max(area.getMinZ(), parentArea.getMinZ());
            int maxZ = Math.min(area.getMaxZ(), parentArea.getMaxZ());
            if (minX > maxX || minZ > maxZ) {
                continue;
            }
            clipped.add(new Rect2i(minX, minZ, maxX, maxZ));
            xEdges.add(Integer.valueOf(minX));
            xEdges.add(Integer.valueOf(maxX + 1));
        }
        if (clipped.isEmpty()) {
            return false;
        }

        Integer previous = null;
        for (Integer edge : xEdges) {
            if (previous != null && !coversZRange(
                    clipped, previous.intValue(), area.getMinZ(),
                    area.getMaxZ())) {
                return false;
            }
            previous = edge;
        }
        return true;
    }

    private static boolean coversZRange(List<Rect2i> rectangles, int x,
                                        int minZ, int maxZ) {
        List<int[]> intervals = new ArrayList<int[]>();
        for (Rect2i rectangle : rectangles) {
            if (rectangle.getMinX() <= x && rectangle.getMaxX() >= x) {
                intervals.add(new int[]{
                        Math.max(minZ, rectangle.getMinZ()),
                        Math.min(maxZ, rectangle.getMaxZ())});
            }
        }
        Collections.sort(intervals, new Comparator<int[]>() {
            @Override
            public int compare(int[] first, int[] second) {
                return first[0] < second[0] ? -1
                        : (first[0] == second[0] ? 0 : 1);
            }
        });
        int coveredUntil = minZ - 1;
        for (int[] interval : intervals) {
            if (interval[0] > coveredUntil + 1) {
                return false;
            }
            coveredUntil = Math.max(coveredUntil, interval[1]);
            if (coveredUntil >= maxZ) {
                return true;
            }
        }
        return coveredUntil >= maxZ;
    }

    static List<Rect2i> mergeChunks(List<Rect2i> existing, Rect2i added) {
        List<Rect2i> result = new ArrayList<Rect2i>(existing);
        result.add(added);
        boolean merged;
        do {
            merged = false;
            outer:
            for (int first = 0; first < result.size(); first++) {
                Rect2i firstArea = result.get(first);
                for (int second = first + 1; second < result.size(); second++) {
                    Rect2i union = mergeRects(firstArea, result.get(second));
                    if (union != null) {
                        result.remove(second);
                        result.set(first, union);
                        merged = true;
                        break outer;
                    }
                }
            }
        } while (merged);
        return result;
    }

    private static Rect2i mergeRects(Rect2i first, Rect2i second) {
        boolean sameX = first.getMinX() == second.getMinX()
                && first.getMaxX() == second.getMaxX();
        boolean touchesZ = first.getMaxZ() + 1 == second.getMinZ()
                || second.getMaxZ() + 1 == first.getMinZ();
        if (sameX && touchesZ) {
            return new Rect2i(first.getMinX(),
                    Math.min(first.getMinZ(), second.getMinZ()),
                    first.getMaxX(),
                    Math.max(first.getMaxZ(), second.getMaxZ()));
        }
        boolean sameZ = first.getMinZ() == second.getMinZ()
                && first.getMaxZ() == second.getMaxZ();
        boolean touchesX = first.getMaxX() + 1 == second.getMinX()
                || second.getMaxX() + 1 == first.getMinX();
        if (sameZ && touchesX) {
            return new Rect2i(Math.min(first.getMinX(), second.getMinX()),
                    first.getMinZ(), Math.max(first.getMaxX(), second.getMaxX()),
                    first.getMaxZ());
        }
        return null;
    }

    static List<Rect2i> subtractChunk(List<Rect2i> areas, Rect2i removed) {
        List<Rect2i> result = new ArrayList<Rect2i>();
        for (Rect2i area : areas) {
            addWithoutChunk(result, area, removed);
        }
        return result;
    }

    private static void addWithoutChunk(List<Rect2i> result, Rect2i area,
                                        Rect2i removed) {
        if (!area.intersects(removed)) {
            result.add(area);
            return;
        }
        int minX = Math.max(area.getMinX(), removed.getMinX());
        int maxX = Math.min(area.getMaxX(), removed.getMaxX());
        int minZ = Math.max(area.getMinZ(), removed.getMinZ());
        int maxZ = Math.min(area.getMaxZ(), removed.getMaxZ());
        addRect(result, area.getMinX(), area.getMinZ(), area.getMaxX(), minZ - 1);
        addRect(result, area.getMinX(), maxZ + 1, area.getMaxX(), area.getMaxZ());
        addRect(result, area.getMinX(), minZ, minX - 1, maxZ);
        addRect(result, maxX + 1, minZ, area.getMaxX(), maxZ);
    }

    private static void addRect(List<Rect2i> result, int minX, int minZ,
                                int maxX, int maxZ) {
        if (minX <= maxX && minZ <= maxZ) {
            result.add(new Rect2i(minX, minZ, maxX, maxZ));
        }
    }
}
