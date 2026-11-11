package dev.che1s.betalands.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class ClaimGeometryTest {
    @Test
    public void acceptsAreaCoveredByAdjacentParentRectangles() {
        Land parent = land(new Rect2i(0, 0, 15, 31),
                new Rect2i(16, 0, 31, 31));

        assertTrue(ClaimGeometry.isAreaInsideParent(
                parent, new Rect2i(0, 0, 31, 31)));
    }

    @Test
    public void rejectsAreaWithAnUncoveredGap() {
        Land parent = land(new Rect2i(0, 0, 15, 31),
                new Rect2i(18, 0, 31, 31));

        assertFalse(ClaimGeometry.isAreaInsideParent(
                parent, new Rect2i(0, 0, 31, 31)));
    }

    @Test
    public void handlesVeryWideSelectionsWithoutBlockWiseScanning() {
        Land parent = land(new Rect2i(0, 0, 999999, 0));

        assertTrue(ClaimGeometry.isAreaInsideParent(
                parent, new Rect2i(0, 0, 999999, 0)));
    }

    private Land land(Rect2i... areas) {
        List<Rect2i> values = new ArrayList<Rect2i>();
        for (Rect2i area : areas) {
            values.add(area);
        }
        return new Land("main", "Alice", "AAA", "world", null,
                values, 0, 127, true, 0L, "manual", null);
    }
}
