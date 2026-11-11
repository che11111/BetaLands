package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class LandImportMergerTest {
    @Test
    public void reimportPreservesFinancialStateAndRefreshesIndexes() {
        LandManager manager = new LandManager(null);
        Land first = imported("towny-1", "Town", 42.0D);
        LandImportResult added = manager.mergeImported(
                Collections.singletonList(first));
        assertEquals(1, added.getAdded());
        assertNotNull(manager.findByName("Town"));

        Land replacement = imported("towny-1", "Town", 0.0D);
        LandImportResult updated = manager.mergeImported(
                Collections.singletonList(replacement));
        assertEquals(1, updated.getUpdated());
        assertEquals(42.0D,
                manager.getById("towny-1").getBankBalance(), 0.0D);
        assertEquals("towny-1",
                manager.findLandAt("world", 8, 64, 8).getId());
    }

    private Land imported(String id, String name, double bank) {
        Land land = new Land(id, "Alice", name, "world", null,
                Collections.singletonList(new Rect2i(0, 0, 15, 15)),
                0, 127, true, 1L, "towny:" + name, null);
        land.setBankBalance(bank);
        return land;
    }
}
