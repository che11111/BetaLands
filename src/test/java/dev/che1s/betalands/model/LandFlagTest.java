package dev.che1s.betalands.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class LandFlagTest {
    @Test
    public void parsesCanonicalNamesAndAliases() {
        assertEquals(LandFlag.EXPLOSION, LandFlag.parse("explosions"));
        assertEquals(LandFlag.FLUID, LandFlag.parse("fluid"));
        assertEquals(LandFlag.MOBS, LandFlag.parse("mob"));
        assertEquals(LandFlag.TRAMPLING, LandFlag.parse("trample"));
        assertEquals(LandFlag.TP, LandFlag.parse("tp"));
        assertNull(LandFlag.parse("unknown"));
    }

    @Test
    public void storesAndClearsLandOverrides() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land land = new Land("alice:aaa", "Alice", "AAA", "world", null,
                areas, 0, 127, true, 0L, "manual", null);
        land.setFlagOverride(LandFlag.FIRE, Boolean.TRUE);
        assertEquals(Boolean.TRUE, land.getFlagOverride(LandFlag.FIRE));
        land.setFlagOverride(LandFlag.FIRE, null);
        assertNull(land.getFlagOverride(LandFlag.FIRE));
        assertEquals(3.0D, land.getRentAmount(), 0.0001D);
    }
}
