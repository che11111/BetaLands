package dev.che1s.betalands.integration;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class MapRenderFingerprintTest {
    @Test
    public void changesWhenDetailsColorOrGeometryChange() {
        Land land = land();
        long baseline = MapRenderFingerprint.of(land, "details", 0x40b4ff);
        assertEquals(baseline, MapRenderFingerprint.of(land, "details", 0x40b4ff));
        assertNotEquals(baseline,
                MapRenderFingerprint.of(land, "changed details", 0x40b4ff));
        assertNotEquals(baseline,
                MapRenderFingerprint.of(land, "details", 0xffa640));

        ArrayList<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 31, 15));
        land.replaceAreas(areas);
        assertNotEquals(baseline,
                MapRenderFingerprint.of(land, "details", 0x40b4ff));
    }

    private Land land() {
        ArrayList<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land("land-1", "Alice", "Alpha", "world", null,
                areas, 0, 127, true, 1L, "manual", null);
    }
}
