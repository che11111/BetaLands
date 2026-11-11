package dev.che1s.betalands.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the current normalized SQL schema. Existing installations are upgraded
 * explicitly outside the plugin, so runtime startup only creates fresh tables.
 */
final class SqlSchema {
    static final String SCHEMA_VERSION = "3";

    private final String prefix;

    SqlSchema(String prefix) {
        this.prefix = sanitizePrefix(prefix);
    }

    String table(String name) {
        return prefix + name;
    }

    void createTables(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("lands") + " ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "name VARCHAR(255) NOT NULL,"
                    + "name_key VARCHAR(255) NOT NULL UNIQUE,"
                    + "owner VARCHAR(255) NOT NULL,"
                    + "owner_key VARCHAR(255) NOT NULL,"
                    + "world VARCHAR(255) NOT NULL,"
                    + "parent_id VARCHAR(64),"
                    + "is_deleted INTEGER NOT NULL,"
                    + "min_y INTEGER NOT NULL,"
                    + "max_y INTEGER NOT NULL,"
                    + "inherit_parent INTEGER NOT NULL,"
                    + "maintenance_exempt INTEGER NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "source VARCHAR(255),"
                    + "home TEXT,"
                    + "flags TEXT,"
                    + "level_id VARCHAR(64),"
                    + "bank_cents BIGINT NOT NULL,"
                    + "deleted_at BIGINT NOT NULL,"
                    + "deleted_by VARCHAR(255),"
                    + "last_maintenance_at BIGINT NOT NULL,"
                    + "rent_enabled INTEGER NOT NULL,"
                    + "rent_amount_cents BIGINT NOT NULL,"
                    + "last_rent_at BIGINT NOT NULL,"
                    + "CONSTRAINT fk_lands_parent FOREIGN KEY (parent_id)"
                    + " REFERENCES " + table("lands") + "(id) ON DELETE CASCADE"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("areas") + " ("
                    + "land_id VARCHAR(64) NOT NULL,"
                    + "ordinal INTEGER NOT NULL,"
                    + "min_x INTEGER NOT NULL,"
                    + "max_x INTEGER NOT NULL,"
                    + "min_z INTEGER NOT NULL,"
                    + "max_z INTEGER NOT NULL,"
                    + "PRIMARY KEY (land_id, ordinal),"
                    + "CONSTRAINT fk_areas_land FOREIGN KEY (land_id)"
                    + " REFERENCES " + table("lands") + "(id) ON DELETE CASCADE"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("members") + " ("
                    + "land_id VARCHAR(64) NOT NULL,"
                    + "player_key VARCHAR(255) NOT NULL,"
                    + "permissions TEXT NOT NULL,"
                    + "PRIMARY KEY (land_id, player_key),"
                    + "CONSTRAINT fk_members_land FOREIGN KEY (land_id)"
                    + " REFERENCES " + table("lands") + "(id) ON DELETE CASCADE"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("rent_accounts") + " ("
                    + "player_key VARCHAR(255) PRIMARY KEY,"
                    + "player_name VARCHAR(255) NOT NULL,"
                    + "balance_cents BIGINT NOT NULL,"
                    + "debt_land_id VARCHAR(64),"
                    + "CONSTRAINT fk_rent_debt_land FOREIGN KEY (debt_land_id)"
                    + " REFERENCES " + table("lands") + "(id) ON DELETE SET NULL"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("players") + " ("
                    + "player_key VARCHAR(255) PRIMARY KEY,"
                    + "last_name VARCHAR(255) NOT NULL,"
                    + "accumulated_seconds BIGINT NOT NULL,"
                    + "level_id VARCHAR(64) NOT NULL,"
                    + "level_managed INTEGER NOT NULL,"
                    + "level_expires_at BIGINT NOT NULL"
                    + ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("metadata") + " ("
                    + "meta_key VARCHAR(128) PRIMARY KEY,"
                    + "meta_value TEXT"
                    + ")");
        } finally {
            close(statement);
        }
        createIndex(connection, prefix + "idx_lands_owner",
                "lands", "owner_key");
        createIndex(connection, prefix + "idx_lands_parent",
                "lands", "parent_id");
        createIndex(connection, prefix + "idx_members_player",
                "members", "player_key");
        createIndex(connection, prefix + "idx_rent_debt_land", "rent_accounts",
                "debt_land_id");
    }

    private void createIndex(Connection connection, String indexName,
                             String tableName, String columns)
            throws SQLException {
        String table = table(tableName);
        if (indexExists(connection, table, indexName)) {
            return;
        }
        Statement statement = connection.createStatement();
        try {
            statement.executeUpdate("CREATE INDEX " + indexName + " ON "
                    + table + " (" + columns + ")");
        } finally {
            close(statement);
        }
    }

    private boolean indexExists(Connection connection, String table,
                                String indexName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        ResultSet indexes = null;
        try {
            indexes = metadata.getIndexInfo(null, null, table, false, false);
            while (indexes.next()) {
                String existing = indexes.getString("INDEX_NAME");
                if (existing != null && existing.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        } finally {
            close(indexes);
        }
    }

    private String sanitizePrefix(String value) {
        String result = value == null ? "betalands_"
                : value.replaceAll("[^A-Za-z0-9_]", "");
        return result.length() == 0 ? "betalands_" : result;
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
