package dev.che1s.betalands.model;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class LandDisplayTest {
    @Test
    public void usesTownyStyleDotForSubclaimDisplay() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land main = new Land("alice:aaa", "Alice", "AAA", "world", null,
                areas, 0, 127, true, 0L, "manual", null);
        Land sub = new Land("alice:aaa/bb", "Alice", "bb", "world", main.getId(),
                areas, 10, 20, true, 0L, "manual", null);
        sub.setParentDisplayName("AAA");
        assertEquals("AAA", main.getDisplayName());
        assertEquals("AAA.bb", sub.getDisplayName());
        assertEquals("AAA", main.getTerritoryName());
        assertEquals("AAA.bb", sub.getTerritoryName());
        main.rename("Qing");
        sub.setParentDisplayName("Qing");
        assertEquals("Qing", main.getDisplayName());
        assertEquals("Qing.bb", sub.getDisplayName());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void memberMapIsReadOnly() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land land = new Land("land", "Alice", "Town", "world", null,
                areas, 0, 127, true, 0L, "manual", null);
        land.setMemberPermissions("Builder",
                java.util.EnumSet.of(LandPermission.BUILD));
        land.getMembers().clear();
    }
}
