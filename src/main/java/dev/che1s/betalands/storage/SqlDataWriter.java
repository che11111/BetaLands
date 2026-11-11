package dev.che1s.betalands.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.util.Text;
import dev.che1s.betalands.util.MoneyCodec;

/**
 * Writes SQL snapshots using per-entity change signatures.
 */
final class SqlDataWriter {
    private final String backend;
    private final SqlSchema schema;
    private final Map<String, String> savedLandSignatures =
            new LinkedHashMap<String, String>();
    private final Map<String, String> savedRentSignatures =
            new LinkedHashMap<String, String>();
    private final Map<String, String> savedPlayerSignatures =
            new LinkedHashMap<String, String>();

    SqlDataWriter(String backend, SqlSchema schema) {
        this.backend = backend;
        this.schema = schema;
    }

    void rememberBaseline(LandManager manager,
                          Map<String, PlayerData> players) {
        savedLandSignatures.clear();
        for (Land land : manager.getPersistedLands()) {
            savedLandSignatures.put(land.getId(), landSignature(land));
        }
        savedRentSignatures.clear();
        for (Map.Entry<String, RentAccount> entry
                : manager.getRentAccounts().entrySet()) {
            savedRentSignatures.put(entry.getKey(),
                    rentSignature(entry.getValue()));
        }
        savedPlayerSignatures.clear();
        for (Map.Entry<String, PlayerData> entry : players.entrySet()) {
            savedPlayerSignatures.put(entry.getKey(),
                    playerSignature(entry.getValue()));
        }
    }

    void save(Connection connection, LandDataSnapshot snapshot)
            throws SQLException {
        connection.setAutoCommit(false);

        Set<String> currentLandIds = new LinkedHashSet<String>();
        List<Land> changedLands = new ArrayList<Land>();
        for (Land land : snapshot.getLands()) {
            currentLandIds.add(land.getId());
            String signature = landSignature(land);
            if (!signature.equals(savedLandSignatures.get(land.getId()))) {
                changedLands.add(land);
            }
        }
        List<Land> changedMains = new ArrayList<Land>();
        List<Land> changedSubclaims = new ArrayList<Land>();
        List<Land> changedArchived = new ArrayList<Land>();
        for (Land land : changedLands) {
            if (land.isDeleted()) {
                changedArchived.add(land);
            } else {
                (land.isSubclaim() ? changedSubclaims : changedMains).add(land);
            }
        }
        for (Land land : changedArchived) {
            saveLandRows(connection, Collections.singletonList(land),
                    savedLandSignatures.containsKey(land.getId()));
        }
        for (Land land : changedMains) {
            saveLandRows(connection, Collections.singletonList(land),
                    savedLandSignatures.containsKey(land.getId()));
        }
        for (Land land : changedSubclaims) {
            saveLandRows(connection, Collections.singletonList(land),
                    savedLandSignatures.containsKey(land.getId()));
        }
        for (Land land : changedLands) {
            deleteLandChildren(connection, land.getId());
            saveAreas(connection, Collections.singletonList(land));
            saveMembers(connection, Collections.singletonList(land));
        }
        for (String removed : new ArrayList<String>(
                savedLandSignatures.keySet())) {
            if (!currentLandIds.contains(removed)) {
                deleteLandRows(connection, removed);
            }
        }

        Set<String> currentRentAccounts = new LinkedHashSet<String>();
        for (Map.Entry<String, RentAccount> entry
                : snapshot.getRentAccounts().entrySet()) {
            currentRentAccounts.add(entry.getKey());
            String signature = rentSignature(entry.getValue());
            if (!signature.equals(savedRentSignatures.get(entry.getKey()))) {
                deleteRentRows(connection, entry.getKey());
                saveRentAccounts(connection, Collections.singletonMap(
                        entry.getKey(), entry.getValue()));
            }
        }
        for (String removed : new ArrayList<String>(
                savedRentSignatures.keySet())) {
            if (!currentRentAccounts.contains(removed)) {
                deleteRentRows(connection, removed);
            }
        }

        Set<String> currentPlayers = new LinkedHashSet<String>();
        for (Map.Entry<String, PlayerData> entry
                : snapshot.getPlayers().entrySet()) {
            currentPlayers.add(entry.getKey());
            String signature = playerSignature(entry.getValue());
            if (!signature.equals(savedPlayerSignatures.get(entry.getKey()))) {
                deletePlayerRow(connection, entry.getKey());
                savePlayers(connection, Collections.singletonMap(
                        entry.getKey(), entry.getValue()));
            }
        }
        for (String removed : new ArrayList<String>(
                savedPlayerSignatures.keySet())) {
            if (!currentPlayers.contains(removed)) {
                deletePlayerRow(connection, removed);
            }
        }

        clearMetadata(connection);
        saveMetadata(connection, snapshot);
        connection.commit();
        rememberSnapshot(snapshot);
    }

    private void rememberSnapshot(LandDataSnapshot snapshot) {
        savedLandSignatures.clear();
        for (Land land : snapshot.getLands()) {
            savedLandSignatures.put(land.getId(), landSignature(land));
        }
        savedRentSignatures.clear();
        for (Map.Entry<String, RentAccount> entry
                : snapshot.getRentAccounts().entrySet()) {
            savedRentSignatures.put(entry.getKey(),
                    rentSignature(entry.getValue()));
        }
        savedPlayerSignatures.clear();
        for (Map.Entry<String, PlayerData> entry
                : snapshot.getPlayers().entrySet()) {
            savedPlayerSignatures.put(entry.getKey(),
                    playerSignature(entry.getValue()));
        }
    }

    private String landSignature(Land land) {
        StringBuilder value = new StringBuilder();
        value.append(land.getId()).append('|').append(land.getName())
                .append('|').append(land.getTerritoryName())
                .append('|').append(land.getOwner()).append('|')
                .append(land.getWorld()).append('|').append(land.getParentId())
                .append('|').append(land.isDeleted()).append('|')
                .append(land.getMinY()).append('|')
                .append(land.getMaxY()).append('|')
                .append(land.inheritsParentPermissions())
                .append('|').append(land.getCreatedAt()).append('|')
                .append(land.getSource()).append('|').append(land.getHome())
                .append('|').append(land.getFlagOverrides()).append('|')
                .append(land.getLevelId()).append('|')
                .append(land.getBankBalance())
                .append('|').append(land.getDeletedAt()).append('|')
                .append(land.getDeletedBy())
                .append('|').append(land.getLastMaintenanceAt()).append('|')
                .append(land.isMaintenanceExempt()).append('|')
                .append(land.isRentEnabled()).append('|')
                .append(land.getRentAmount()).append('|')
                .append(land.getLastRentAt()).append('|');
        for (Rect2i area : land.getAreas()) {
            value.append(area.serialize()).append(';');
        }
        value.append('|');
        for (Map.Entry<String, EnumSet<LandPermission>> entry
                : land.getMembers().entrySet()) {
            value.append(entry.getKey()).append('=')
                    .append(entry.getValue()).append(';');
        }
        return value.toString();
    }

    private String rentSignature(RentAccount account) {
        return account.getPlayerName() + "|" + account.getBalance()
                + "|" + account.getDebtLandId();
    }

    private String playerSignature(PlayerData data) {
        return data.getLastName() + "|" + data.getAccumulatedSeconds()
                + "|" + data.getLevelId() + "|" + data.isLevelManaged()
                + "|" + data.getLevelExpiresAt();
    }

    private void deleteLandRows(Connection connection, String landId)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(
                    "DELETE FROM " + schema.table("areas")
                            + " WHERE land_id = ?");
            statement.setString(1, landId);
            statement.executeUpdate();
            statement.close();
            statement = connection.prepareStatement(
                    "DELETE FROM " + schema.table("members")
                            + " WHERE land_id = ?");
            statement.setString(1, landId);
            statement.executeUpdate();
            statement.close();
            statement = connection.prepareStatement(
                    "DELETE FROM " + schema.table("lands")
                            + " WHERE id = ?");
            statement.setString(1, landId);
            statement.executeUpdate();
        } finally {
            close(statement);
        }
    }

    private void deleteLandChildren(Connection connection, String landId)
            throws SQLException {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement(
                    "DELETE FROM " + schema.table("areas")
                            + " WHERE land_id = ?");
            statement.setString(1, landId);
            statement.executeUpdate();
            statement.close();
            statement = connection.prepareStatement(
                    "DELETE FROM " + schema.table("members")
                            + " WHERE land_id = ?");
            statement.setString(1, landId);
            statement.executeUpdate();
        } finally {
            close(statement);
        }
    }

    private void deleteRentRows(Connection connection, String playerKey)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + schema.table("rent_accounts")
                        + " WHERE player_key = ?");
        try {
            statement.setString(1, playerKey);
            statement.executeUpdate();
        } finally {
            close(statement);
        }
    }

    private void deletePlayerRow(Connection connection, String playerKey)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + schema.table("players")
                        + " WHERE player_key = ?");
        try {
            statement.setString(1, playerKey);
            statement.executeUpdate();
        } finally {
            close(statement);
        }
    }

    private void clearMetadata(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("DELETE FROM " + schema.table("metadata"));
        } finally {
            close(statement);
        }
    }

    private void saveAreas(Connection connection, List<Land> lands)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema.table("areas")
                        + " (land_id, ordinal, min_x, max_x, min_z, max_z)"
                        + " VALUES (?, ?, ?, ?, ?, ?)");
        try {
            for (Land land : lands) {
                int ordinal = 0;
                for (Rect2i area : land.getAreas()) {
                    statement.setString(1, land.getId());
                    statement.setInt(2, ordinal++);
                    statement.setInt(3, area.getMinX());
                    statement.setInt(4, area.getMaxX());
                    statement.setInt(5, area.getMinZ());
                    statement.setInt(6, area.getMaxZ());
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        } finally {
            close(statement);
        }
    }

    private void saveLandRows(Connection connection, List<Land> lands,
                              boolean update) throws SQLException {
        if (lands.isEmpty()) {
            return;
        }
        String fields = "name=?, name_key=?, owner=?, owner_key=?, world=?,"
                + " parent_id=?, is_deleted=?, min_y=?, max_y=?, inherit_parent=?,"
                + " maintenance_exempt=?, created_at=?, source=?, home=?,"
                + " flags=?, level_id=?, bank_cents=?,"
                + " deleted_at=?, deleted_by=?,"
                + " last_maintenance_at=?, rent_enabled=?,"
                + " rent_amount_cents=?, last_rent_at=?";
        String sql = update
                ? "UPDATE " + schema.table("lands") + " SET " + fields
                        + " WHERE id=?"
                : "INSERT INTO " + schema.table("lands")
                        + " (id, name, name_key, owner, owner_key, world,"
                        + " parent_id, is_deleted, min_y, max_y, inherit_parent,"
                        + " maintenance_exempt, created_at, source, home,"
                        + " flags, level_id, bank_cents, deleted_at, deleted_by,"
                        + " last_maintenance_at,"
                        + " rent_enabled, rent_amount_cents, last_rent_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (Land land : lands) {
                int index = 1;
                if (!update) {
                    statement.setString(index++, land.getId());
                }
                index = setLandFields(statement, land, index);
                if (update) {
                    statement.setString(index, land.getId());
                }
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            close(statement);
        }
    }

    private int setLandFields(PreparedStatement statement, Land land,
                              int index) throws SQLException {
        statement.setString(index++, land.getName());
        statement.setString(index++, land.getStorageNameKey());
        statement.setString(index++, land.getOwner());
        statement.setString(index++, Text.key(land.getOwner()));
        statement.setString(index++, land.getWorld());
        statement.setString(index++, land.getParentId());
        statement.setInt(index++, land.isDeleted() ? 1 : 0);
        statement.setInt(index++, land.getMinY());
        statement.setInt(index++, land.getMaxY());
        statement.setInt(index++, land.inheritsParentPermissions() ? 1 : 0);
        statement.setInt(index++, land.isMaintenanceExempt() ? 1 : 0);
        statement.setLong(index++, land.getCreatedAt());
        statement.setString(index++, land.getSource());
        statement.setString(index++, land.getHome() == null
                ? null : land.getHome().serialize());
        statement.setString(index++, DataStore.serializeFlags(land));
        statement.setString(index++, land.getLevelId());
        statement.setLong(index++, MoneyCodec.toCents(land.getBankBalance()));
        statement.setLong(index++, land.getDeletedAt());
        statement.setString(index++, land.getDeletedBy());
        statement.setLong(index++, land.getLastMaintenanceAt());
        statement.setInt(index++, land.isRentEnabled() ? 1 : 0);
        statement.setLong(index++, MoneyCodec.toCents(land.getRentAmount()));
        statement.setLong(index++, land.getLastRentAt());
        return index;
    }

    private void saveMembers(Connection connection, List<Land> lands)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema.table("members")
                        + " (land_id, player_key, permissions)"
                        + " VALUES (?, ?, ?)");
        try {
            for (Land land : lands) {
                for (Map.Entry<String, EnumSet<LandPermission>> entry
                        : land.getMembers().entrySet()) {
                    statement.setString(1, land.getId());
                    statement.setString(2, entry.getKey());
                    statement.setString(3,
                            StorageCodec.permissions(entry.getValue()));
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        } finally {
            close(statement);
        }
    }

    private void saveRentAccounts(Connection connection,
                                  Map<String, RentAccount> accounts)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema.table("rent_accounts")
                        + " (player_key, player_name, balance_cents, debt_land_id)"
                        + " VALUES (?, ?, ?, ?)");
        try {
            for (Map.Entry<String, RentAccount> entry : accounts.entrySet()) {
                RentAccount account = entry.getValue();
                statement.setString(1, entry.getKey());
                statement.setString(2, account.getPlayerName());
                statement.setLong(3,
                        MoneyCodec.toCents(account.getBalance()));
                statement.setString(4, account.getDebtLandId());
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            close(statement);
        }
    }

    private void savePlayers(Connection connection,
                             Map<String, PlayerData> players)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema.table("players")
                        + " (player_key, last_name, accumulated_seconds,"
                        + " level_id, level_managed, level_expires_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)");
        long now = System.currentTimeMillis();
        try {
            for (PlayerData data : players.values()) {
                statement.setString(1, data.getKey());
                statement.setString(2, data.getLastName());
                statement.setLong(3, data.getTotalSeconds(now));
                statement.setString(4, data.getLevelId());
                statement.setInt(5, data.isLevelManaged() ? 1 : 0);
                statement.setLong(6, data.getLevelExpiresAt());
                statement.addBatch();
            }
            statement.executeBatch();
        } finally {
            close(statement);
        }
    }

    private void saveMetadata(Connection connection,
                              LandDataSnapshot snapshot)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema.table("metadata")
                        + " (meta_key, meta_value) VALUES (?, ?)");
        try {
            statement.setString(1, "schema_version");
            statement.setString(2, SqlSchema.SCHEMA_VERSION);
            statement.addBatch();
            statement.setString(1, "backend");
            statement.setString(2, backend);
            statement.addBatch();
            statement.setString(1, "server_maintenance_bank_cents");
            statement.setString(2,
                    Long.toString(MoneyCodec.toCents(
                            snapshot.getServerMaintenanceBank())));
            statement.addBatch();
            statement.executeBatch();
        } finally {
            close(statement);
        }
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
