package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.Assume;
import org.bukkit.entity.Player;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.service.ClaimService;
import dev.che1s.betalands.service.PermissionService;

public class SqlDataStoreRoundTripTest {
    @Test
    public void softDeletedLandRemainsStoredAndCanBeRecreated() throws Exception {
        File database = File.createTempFile("betalands-soft-delete-", ".db");
        if (!database.delete()) {
            throw new IllegalStateException("Could not prepare test database");
        }
        try {
            LandManager source = new LandManager(null);
            Land original = land("archived", "Alice", "AAA", null,
                    new Rect2i(0, 0, 15, 15));
            source.addLoadedLand(original);
            new ClaimService(null, source,
                    new PermissionService(null, source))
                    .deleteLand(admin(), original, true);

            SqlDataStore store = new SqlDataStore(
                    "sqlite", "betalands_", database);
            store.saveSnapshot(LandDataSnapshot.capture(
                    source, System.currentTimeMillis()));

            LandManager loaded = new LandManager(null);
            store.load(loaded, new LinkedHashMap<String, PlayerData>());
            assertNull(loaded.getById("archived"));
            assertNotNull(loaded.getByIdIncludingDeleted("archived"));
            assertTrue(loaded.getByIdIncludingDeleted("archived").isDeleted());
            assertEquals("Admin",
                    loaded.getByIdIncludingDeleted("archived").getDeletedBy());

            SqlDataStore reloadedStore = new SqlDataStore(
                    "sqlite", "betalands_", database);
            reloadedStore.load(loaded,
                    new LinkedHashMap<String, PlayerData>());
            reloadedStore.saveSnapshot(LandDataSnapshot.capture(
                    loaded, System.currentTimeMillis()));

            Land replacement = land("replacement", "Bob", "AAA", null,
                    new Rect2i(32, 0, 47, 15));
            loaded.addLoadedLand(replacement);
            assertEquals("replacement", loaded.findByName("AAA").getId());
            reloadedStore.saveSnapshot(LandDataSnapshot.capture(
                    loaded, System.currentTimeMillis()));

            LandManager finalLoad = new LandManager(null);
            reloadedStore.load(finalLoad,
                    new LinkedHashMap<String, PlayerData>());
            assertEquals("replacement", finalLoad.findByName("AAA").getId());
            assertEquals("archived",
                    finalLoad.getDeletedLands().get(0).getId());
        } finally {
            database.delete();
        }
    }

    @Test
    public void loadsConfiguredExistingDatabase() throws Exception {
        String configured = System.getenv("BETALANDS_TEST_DATABASE");
        Assume.assumeNotNull(configured);
        File database = new File(configured);
        Assume.assumeTrue(database.isFile());

        LandManager loaded = new LandManager(null);
        Map<String, PlayerData> players =
                new LinkedHashMap<String, PlayerData>();
        new SqlDataStore("sqlite", "betalands_", database)
                .load(loaded, players);

        assertFalse(loaded.getLands().isEmpty());
    }

    @Test
    public void savesAndReloadsCompleteLandState() throws Exception {
        File database = File.createTempFile("betalands-roundtrip-", ".db");
        if (!database.delete()) {
            throw new IllegalStateException("Could not prepare test database");
        }
        try {
            LandManager source = new LandManager(null);
            Land main = land("main", "Alice", "Town", null,
                    new Rect2i(0, 0, 31, 15));
            source.addLoadedLand(main);
            main.setBankBalance(42.5D);
            main.setMaintenanceExempt(true);
            main.setFlagOverride(LandFlag.FIRE, Boolean.FALSE);
            main.setMemberPermissions("Builder",
                    EnumSet.of(LandPermission.BUILD, LandPermission.CONTAINER));

            Land sub = land("sub", "Alice", "Shop", main.getId(),
                    new Rect2i(0, 0, 15, 15));
            source.addLoadedLand(sub);
            sub.setHeightRange(20, 40);
            source.getOrCreateRentAccount("Builder").addBalance(12.0D);
            PlayerData player = new PlayerData("builder", "Builder", 3600L);
            player.setManagedLevel("vip", 123456789L);
            source.getPlayers().put("builder", player);

            SqlDataStore store = new SqlDataStore(
                    "sqlite", "betalands_", database);
            store.saveSnapshot(LandDataSnapshot.capture(
                    source, System.currentTimeMillis()));

            LandManager loaded = new LandManager(null);
            Map<String, PlayerData> players = new LinkedHashMap<String, PlayerData>();
            store.load(loaded, players);

            Land loadedMain = loaded.getById("main");
            Land loadedSub = loaded.getById("sub");
            assertNotNull(loadedMain);
            assertNotNull(loadedSub);
            assertEquals("Town", loadedMain.getTerritoryName());
            assertEquals(42.5D, loadedMain.getBankBalance(), 0.0D);
            assertTrue(loadedMain.isMaintenanceExempt());
            assertFalse(loadedMain.getFlagOverride(LandFlag.FIRE));
            assertEquals(EnumSet.of(LandPermission.BUILD,
                    LandPermission.CONTAINER),
                    loadedMain.getMemberPermissions("Builder"));
            assertEquals("Town.Shop", loadedSub.getTerritoryName());
            assertEquals(20, loadedSub.getMinY());
            assertEquals(40, loadedSub.getMaxY());
            assertEquals(12.0D, loaded.getRentAccount("Builder").getBalance(), 0.0D);
            assertEquals("vip", players.get("builder").getLevelId());
            assertEquals(123456789L,
                    players.get("builder").getLevelExpiresAt());

            main.setBankBalance(99.0D);
            source.getPlayers().clear();
            store.saveSnapshot(LandDataSnapshot.capture(
                    source, System.currentTimeMillis()));
            Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + database.getAbsolutePath());
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM betalands_players");
            try {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            } finally {
                rows.close();
                statement.close();
                connection.close();
            }
            LandManager incrementallyReloaded = new LandManager(null);
            Map<String, PlayerData> reloadedPlayers =
                    new LinkedHashMap<String, PlayerData>();
            store.load(incrementallyReloaded, reloadedPlayers);
            assertEquals(99.0D,
                    incrementallyReloaded.getById("main").getBankBalance(), 0.0D);
            assertTrue(reloadedPlayers.toString(), reloadedPlayers.isEmpty());
        } finally {
            database.delete();
        }
    }

    private Land land(String id, String owner, String name, String parentId,
                      Rect2i area) {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(area);
        return new Land(id, owner, name, "world", parentId,
                areas, 0, 127, true, System.currentTimeMillis(), "manual", null);
    }

    private Player admin() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "Admin";
                    }
                    if ("isOp".equals(method.getName())
                            || "hasPermission".equals(method.getName())) {
                        return true;
                    }
                    if ("toString".equals(method.getName())) {
                        return "Admin";
                    }
                    return null;
                });
    }
}
