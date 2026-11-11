package dev.che1s.betalands.integration;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

final class MapRenderFingerprint {
    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private MapRenderFingerprint() {
    }

    static long of(Land land, String details, int color) {
        long hash = OFFSET_BASIS;
        hash = addString(hash, land.getId());
        hash = addString(hash, land.getDisplayName());
        hash = addString(hash, land.getWorld());
        hash = addString(hash, details);
        hash = addInt(hash, color);
        hash = addInt(hash, land.getMinY());
        hash = addInt(hash, land.getMaxY());
        for (Rect2i area : land.getAreas()) {
            hash = addInt(hash, area.getMinX());
            hash = addInt(hash, area.getMinZ());
            hash = addInt(hash, area.getMaxX());
            hash = addInt(hash, area.getMaxZ());
        }
        return hash;
    }

    private static long addString(long hash, String value) {
        if (value == null) {
            return addInt(hash, 0);
        }
        long result = hash;
        for (int index = 0; index < value.length(); index++) {
            result ^= value.charAt(index);
            result *= PRIME;
        }
        return result;
    }

    private static long addInt(long hash, int value) {
        long result = hash;
        for (int shift = 0; shift < 32; shift += 8) {
            result ^= (value >>> shift) & 0xff;
            result *= PRIME;
        }
        return result;
    }
}
