package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.AdminRegionPermission;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class AdminProtectedRegionTest {
    @Test
    public void twoDimensionalRegionCoversFullHeight() {
        AdminProtectedRegion region = region("spawn", false, 0, 127,
                new Rect2i(0, 0, 31, 31));

        assertTrue(region.contains("world", 10, 0, 10));
        assertTrue(region.contains("world", 10, 127, 10));
        assertFalse(region.contains("nether", 10, 64, 10));
    }

    @Test
    public void threeDimensionalRegionHonorsYRange() {
        AdminProtectedRegion region = region("bank", true, 40, 80,
                new Rect2i(0, 0, 15, 15));

        assertTrue(region.contains("world", 1, 40, 1));
        assertTrue(region.contains("world", 1, 80, 1));
        assertFalse(region.contains("world", 1, 39, 1));
        assertFalse(region.contains("world", 1, 81, 1));
    }

    @Test
    public void whitelistDefaultsContainAllOperationalPermissions() {
        AdminProtectedRegion region = region("spawn", false, 0, 127,
                new Rect2i(0, 0, 15, 15));
        region.setWhitelistPermissions("Builder",
                EnumSet.copyOf(AdminRegionPermission.defaults()));

        assertTrue(region.isWhitelisted("builder"));
        assertTrue(region.hasPermission("Builder",
                AdminRegionPermission.LANDCREATE));
        assertTrue(region.hasPermission("Builder",
                AdminRegionPermission.LANDCLAIM));
        assertTrue(region.hasPermission("Builder",
                AdminRegionPermission.BUILD));
    }

    @Test
    public void managerFindsRegionsOverlappingRegularLand() {
        AdminRegionManager manager = new AdminRegionManager();
        AdminProtectedRegion region = region("spawn", true, 0, 127,
                new Rect2i(0, 0, 31, 31));
        manager.addRegion(region);
        Land main = land("main", null, 0, 127,
                new Rect2i(16, 16, 47, 47));

        assertEquals(region, manager.findAt("world", 20, 64, 20));
        assertEquals(region, manager.findForLand(main));
        assertNull(manager.findOverlapping("world",
                singleton(new Rect2i(32, 32, 63, 63)),
                0, 127, true, region));
        assertNotNull(manager.findOverlapping("world",
                singleton(new Rect2i(16, 16, 47, 47)),
                0, 127, true, null));
    }

    @Test
    public void threeDimensionalRegionsCanBeSeparatedByHeight() {
        AdminRegionManager manager = new AdminRegionManager();
        AdminProtectedRegion lower = region("lower", true, 0, 20,
                new Rect2i(0, 0, 15, 15));
        manager.addRegion(lower);

        assertNull(manager.findOverlapping("world",
                singleton(new Rect2i(0, 0, 15, 15)),
                40, 60, true, null));
        assertNotNull(manager.findOverlapping("world",
                singleton(new Rect2i(0, 0, 15, 15)),
                10, 50, true, null));
    }

    private AdminProtectedRegion region(String name, boolean threeDimensional,
                                        int minY, int maxY, Rect2i area) {
        return new AdminProtectedRegion(name, name, "world", "Admin",
                1000L, threeDimensional, minY, maxY, singleton(area));
    }

    private Land land(String name, String parentId, int minY, int maxY,
                      Rect2i area) {
        return new Land(name, "Owner", name, "world", parentId,
                singleton(area), minY, maxY, true, 1000L, "manual", null);
    }

    private List<Rect2i> singleton(Rect2i area) {
        List<Rect2i> result = new ArrayList<Rect2i>();
        result.add(area);
        return result;
    }
}
