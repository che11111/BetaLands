package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.service.LandManager;

public class MySqlDataStoreRoundTripTest {
    @Test
    public void savesAndReloadsCompleteStateWhenMySqlIsConfigured()
            throws Exception {
        String host = System.getenv("BETALANDS_TEST_MYSQL_HOST");
        String database = System.getenv("BETALANDS_TEST_MYSQL_DATABASE");
        String username = System.getenv("BETALANDS_TEST_MYSQL_USERNAME");
        Assume.assumeNotNull(host, database, username);

        int port = Integer.parseInt(System.getenv("BETALANDS_TEST_MYSQL_PORT") == null
                ? "3306" : System.getenv("BETALANDS_TEST_MYSQL_PORT"));
        String password = System.getenv("BETALANDS_TEST_MYSQL_PASSWORD");
        if (password == null) {
            password = "";
        }
        String prefix = "betalands_test_"
                + Long.toHexString(System.currentTimeMillis()) + "_";

        LandManager source = new LandManager(null);
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land land = new Land("mysql-land", "Alice", "MySQLTown",
                "world", null, areas, 0, 127, true,
                System.currentTimeMillis(), "manual", null);
        source.addLoadedLand(land);
        land.setBankBalance(17.25D);
        source.getPlayers().put("alice",
                new PlayerData("alice", "Alice", 120L));

        SqlDataStore store = new SqlDataStore(
                prefix, host, port, database, username, password, false, 5);
        store.saveSnapshot(LandDataSnapshot.capture(
                source, System.currentTimeMillis()));

        LandManager loaded = new LandManager(null);
        Map<String, PlayerData> players = new LinkedHashMap<String, PlayerData>();
        store.load(loaded, players);
        assertNotNull(loaded.getById("mysql-land"));
        assertEquals("MySQLTown",
                loaded.getById("mysql-land").getTerritoryName());
        assertEquals(17.25D,
                loaded.getById("mysql-land").getBankBalance(), 0.0D);
        assertEquals("Alice", players.get("alice").getLastName());
    }
}
