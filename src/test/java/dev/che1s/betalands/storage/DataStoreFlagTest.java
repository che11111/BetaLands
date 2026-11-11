package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.Rect2i;

public class DataStoreFlagTest {
    @Test
    public void roundTripsLandFlagsInLegacyReservedColumn() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land land = new Land("alice:aaa", "Alice", "AAA", "world", null,
                areas, 0, 127, true, 0L, "manual", null);
        land.setFlagOverride(LandFlag.FIRE, Boolean.TRUE);
        land.setFlagOverride(LandFlag.PVP, Boolean.FALSE);

        String serialized = DataStore.serializeFlags(land);
        Map<LandFlag, Boolean> parsed = DataStore.parseFlags(serialized);
        assertTrue(parsed.get(LandFlag.FIRE).booleanValue());
        assertFalse(parsed.get(LandFlag.PVP).booleanValue());
        assertEquals(2, parsed.size());
    }

    @Test
    public void ignoresLegacyReservedValue() {
        assertTrue(DataStore.parseFlags("0").isEmpty());
        assertTrue(DataStore.parseFlags("-").isEmpty());
    }
}
