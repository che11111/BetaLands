package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class TerritoryTrackerTest {
    @Test
    public void ignoresInitialAndUnchangedTerritory() {
        TerritoryTracker tracker = new TerritoryTracker();
        Land land = land("alice:aaa", "AAA", null);

        assertFalse(tracker.update("Alice", null).changed());
        tracker.remember("Alice", land);
        assertFalse(tracker.update("alice", land).changed());
        assertEquals(land, tracker.get("ALICE"));
    }

    @Test
    public void reportsEnteringAndLeavingWilderness() {
        TerritoryTracker tracker = new TerritoryTracker();
        Land land = land("alice:aaa", "AAA", null);

        tracker.remember("Alice", null);
        TerritoryTracker.Change enter = tracker.update("Alice", land);
        assertTrue(enter.changed());
        assertNull(enter.getPrevious());
        assertEquals(land, enter.getCurrent());

        TerritoryTracker.Change leave = tracker.update("Alice", null);
        assertTrue(leave.changed());
        assertEquals(land, leave.getPrevious());
        assertNull(leave.getCurrent());
    }

    @Test
    public void reportsChangingBetweenSubclaimAndParent() {
        TerritoryTracker tracker = new TerritoryTracker();
        Land main = land("alice:aaa", "AAA", null);
        Land sub = land("alice:aaa/bb", "BB", main.getId());

        tracker.remember("Alice", main);
        TerritoryTracker.Change change = tracker.update("Alice", sub);
        assertTrue(change.changed());
        assertEquals(main, change.getPrevious());
        assertEquals(sub, change.getCurrent());
    }

    private Land land(String id, String name, String parentId) {
        return new Land(id, "Alice", name, "world", parentId,
                Collections.singletonList(new Rect2i(0, 0, 15, 15)),
                0, 127, true, 0L, "manual", null);
    }
}
