package dev.che1s.betalands.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.util.MoneyCodec;

/**
 * Reads the normalized SQL schema into the in-memory land manager.
 */
final class SqlDataLoader {
    private final BetaLandsPlugin plugin;
    private final SqlSchema schema;
    private int loadErrors;

    SqlDataLoader(BetaLandsPlugin plugin, SqlSchema schema) {
        this.plugin = plugin;
        this.schema = schema;
    }

    int load(Connection connection, LandManager manager,
             Map<String, PlayerData> players) throws SQLException {
        manager.clear();
        players.clear();
        loadErrors = 0;
        Map<String, List<Rect2i>> areas = loadAreas(connection);
        loadLands(connection, manager, areas);
        loadMaintenanceExemptions(connection, manager);
        loadMembers(connection, manager);
        loadRentAccounts(connection, manager);
        loadPlayers(connection, players);
        manager.rebuildIndexes();
        return loadErrors;
    }

    private Map<String, List<Rect2i>> loadAreas(Connection connection)
            throws SQLException {
        Map<String, List<Rect2i>> areas = new HashMap<String, List<Rect2i>>();
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery("SELECT land_id, min_x, max_x, min_z, max_z "
                    + "FROM " + schema.table("areas")
                    + " ORDER BY land_id, ordinal");
            while (rows.next()) {
                String landId = rows.getString("land_id");
                List<Rect2i> list = areas.get(landId);
                if (list == null) {
                    list = new ArrayList<Rect2i>();
                    areas.put(landId, list);
                }
                list.add(new Rect2i(rows.getInt("min_x"), rows.getInt("min_z"),
                        rows.getInt("max_x"), rows.getInt("max_z")));
            }
        } finally {
            close(rows);
            close(statement);
        }
        return areas;
    }

    private void loadLands(Connection connection, LandManager manager,
                           Map<String, List<Rect2i>> areaCache)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery("SELECT id, name, owner, world, parent_id, "
                    + "is_deleted, min_y, max_y, inherit_parent, maintenance_exempt,"
                    + " created_at, source, home, flags, level_id, bank_cents,"
                    + " deleted_at, deleted_by, last_maintenance_at,"
                    + " rent_enabled, rent_amount_cents, last_rent_at FROM "
                    + schema.table("lands"));
            while (rows.next()) {
                String id = rows.getString("id");
                List<Rect2i> areas = areaCache.get(id);
                if (areas == null || areas.isEmpty()) {
                    loadErrors++;
                    continue;
                }
                try {
                    Land land = new Land(id, rows.getString("owner"),
                            rows.getString("name"), rows.getString("world"),
                            blankToNull(rows.getString("parent_id")), areas,
                            rows.getInt("min_y"), rows.getInt("max_y"),
                            rows.getInt("inherit_parent") != 0,
                            rows.getLong("created_at"), rows.getString("source"),
                            HomePoint.parse(blankToNull(rows.getString("home"))));
                    land.setFlagOverrides(
                            DataStore.parseFlags(rows.getString("flags")));
                    land.setLevelId(rows.getString("level_id"));
                    land.setBankBalance(MoneyCodec.fromCents(
                            rows.getLong("bank_cents")));
                    long deletedAt = rows.getLong("deleted_at");
                    if (rows.getInt("is_deleted") != 0 || deletedAt > 0L) {
                        land.markDeleted(rows.getString("deleted_by"),
                                deletedAt);
                    }
                    land.setLastMaintenanceAt(
                            rows.getLong("last_maintenance_at"));
                    land.setRentEnabled(rows.getInt("rent_enabled") != 0);
                    land.setRentAmount(MoneyCodec.fromCents(
                            rows.getLong("rent_amount_cents")));
                    land.setMaintenanceExempt(
                            rows.getInt("maintenance_exempt") != 0);
                    land.setLastRentAt(rows.getLong("last_rent_at"));
                    manager.addLoadedLand(land);
                } catch (RuntimeException exception) {
                    loadErrors++;
                    if (plugin != null) {
                        plugin.getMessages().warning(
                                "data.database-invalid-land",
                                "Skipped invalid land {id}: {error}",
                                "id", id,
                                "error", exception.getMessage());
                    }
                }
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private void loadMembers(Connection connection, LandManager manager)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery(
                    "SELECT land_id, player_key, permissions FROM "
                            + schema.table("members"));
            while (rows.next()) {
                Land land = manager.getByIdIncludingDeleted(
                        rows.getString("land_id"));
                if (land == null) {
                    loadErrors++;
                    continue;
                }
                EnumSet<LandPermission> permissions =
                        StorageCodec.parsePermissions(
                                rows.getString("permissions"));
                land.setMemberPermissions(
                        rows.getString("player_key"), permissions);
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private void loadMaintenanceExemptions(Connection connection,
                                           LandManager manager)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery(
                    "SELECT meta_key, meta_value FROM "
                            + schema.table("metadata")
                            + " WHERE meta_key='server_maintenance_bank_cents'");
            while (rows.next()) {
                String key = rows.getString("meta_key");
                String value = rows.getString("meta_value");
                if ("server_maintenance_bank_cents".equals(key)) {
                    manager.setServerMaintenanceBank(
                            MoneyCodec.fromCents(parseLong(value)));
                }
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private void loadRentAccounts(Connection connection, LandManager manager)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery(
                    "SELECT player_key, player_name, balance_cents,"
                            + " debt_land_id FROM "
                            + schema.table("rent_accounts"));
            while (rows.next()) {
                RentAccount account = manager.getOrCreateRentAccount(
                        rows.getString("player_name"));
                account.setBalance(MoneyCodec.fromCents(
                        rows.getLong("balance_cents")));
                account.setDebtLandId(
                        blankToNull(rows.getString("debt_land_id")));
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private void loadPlayers(Connection connection,
                             Map<String, PlayerData> players)
            throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery(
                    "SELECT player_key, last_name, accumulated_seconds, "
                            + "level_id, level_managed, level_expires_at FROM "
                            + schema.table("players"));
            while (rows.next()) {
                String key = rows.getString("player_key");
                PlayerData data = new PlayerData(key,
                        rows.getString("last_name"),
                        rows.getLong("accumulated_seconds"));
                data.setLevelId(rows.getString("level_id"));
                if (rows.getInt("level_managed") != 0) {
                    data.setManagedLevel(rows.getString("level_id"),
                            rows.getLong("level_expires_at"));
                }
                players.put(key, data);
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.length() == 0 ? null : value;
    }

    private void close(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
