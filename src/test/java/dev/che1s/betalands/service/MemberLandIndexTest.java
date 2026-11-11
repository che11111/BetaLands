package dev.che1s.betalands.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.Rect2i;

public class MemberLandIndexTest {
    @Test
    public void indexesMemberLandsByPlayerWithoutFullScans() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        main.setMemberPermissions("Builder",
                EnumSet.of(LandPermission.BUILD));
        manager.refreshMemberIndex(main);

        assertTrue(manager.getMemberLands("builder").contains(main));

        main.removeMember("Builder");
        manager.refreshMemberIndex(main);
        assertFalse(manager.getMemberLands("builder").contains(main));
    }

    private Land land() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land("main", "Alice", "AAA", "world", null, areas,
                0, 127, true, 0L, "manual", null);
    }
}
