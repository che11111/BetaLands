package dev.che1s.betalands.integration;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import dev.che1s.betalands.model.Rect2i;

public class TownyImporterTest {
    @Test
    public void mergesCompleteRowsWithoutChangingCoverage() {
        Set<Long> chunks = new LinkedHashSet<Long>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 2; z++) {
                chunks.add(TownyImporter.pack(x, z));
            }
        }
        List<Rect2i> areas = TownyImporter.decompose(chunks);
        assertEquals(1, areas.size());
        assertEquals(3 * 2 * 256L, areas.get(0).area());
    }

    @Test
    public void decomposesIrregularTownyShapeExactly() {
        Set<Long> chunks = new LinkedHashSet<Long>();
        chunks.add(TownyImporter.pack(0, 0));
        chunks.add(TownyImporter.pack(1, 0));
        chunks.add(TownyImporter.pack(0, 1));
        List<Rect2i> areas = TownyImporter.decompose(chunks);
        long area = 0L;
        for (Rect2i rectangle : areas) {
            area += rectangle.area();
        }
        assertEquals(3L * 16L * 16L, area);
    }

    @Test
    public void keepsOriginalTownyNameWhenAvailable() {
        Set<String> occupied = new HashSet<String>();
        assertEquals("Qing", TownyImporter.chooseUniqueMainName(
                "Qing", occupied, 2, 24));
        assertEquals("SEN-44444", TownyImporter.chooseUniqueMainName(
                "SEN-44444.", occupied, 2, 24));
    }

    @Test
    public void usesTownyPrefixOnlyForDuplicateNames() {
        Set<String> occupied = new HashSet<String>();
        occupied.add("qing");
        assertEquals("towny-Qing", TownyImporter.chooseUniqueMainName(
                "Qing", occupied, 2, 24));
        assertEquals("towny-Qing-2", TownyImporter.chooseUniqueMainName(
                "Qing", occupied, 2, 24));
    }
}
