package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.AdminRegionPermission;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.service.AdminRegionManager;
import dev.che1s.betalands.service.LandManager;

public class AdminRegionStoreRoundTripTest {
    @Test
    public void persistsRegionsWhitelistsAndFlags() throws Exception {
        File database = File.createTempFile("betalands-admin-region-", ".db");
        assertTrue(database.delete());
        try {
            LandManager lands = new LandManager(null);
            AdminRegionManager regions = new AdminRegionManager();
            AdminProtectedRegion region = new AdminProtectedRegion(
                    "region", "Spawn", "world", "Admin", 1000L,
                    true, 40, 80,
                    Arrays.asList(new Rect2i(-32, -32, 63, 63)));
            region.setWhitelistPermissions("Builder",
                    EnumSet.of(AdminRegionPermission.LANDCLAIM,
                            AdminRegionPermission.BUILD,
                            AdminRegionPermission.BREAK));
            region.setFlagOverride(LandFlag.PVP, Boolean.FALSE);
            regions.addRegion(region);

            SqlDataStore store = new SqlDataStore(
                    "sqlite", "betalands_", database);
            store.saveSnapshot(LandDataSnapshot.capture(
                    lands, regions, System.currentTimeMillis()));

            LandManager loadedLands = new LandManager(null);
            AdminRegionManager loadedRegions = new AdminRegionManager();
            Map<String, PlayerData> players =
                    new LinkedHashMap<String, PlayerData>();
            store.load(loadedLands, loadedRegions, players);

            AdminProtectedRegion loaded =
                    loadedRegions.resolve("Spawn");
            assertNotNull(loaded);
            assertTrue(loaded.isThreeDimensional());
            assertEquals(40, loaded.getMinY());
            assertEquals(80, loaded.getMaxY());
            assertTrue(loaded.hasPermission("Builder",
                    AdminRegionPermission.BUILD));
            assertEquals(Boolean.FALSE,
                    loaded.getFlagOverride(LandFlag.PVP));
        } finally {
            database.delete();
        }
    }
}
