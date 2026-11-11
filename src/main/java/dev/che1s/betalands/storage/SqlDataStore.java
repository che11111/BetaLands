package dev.che1s.betalands.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.util.Text;

/**
 * Coordinates SQL persistence without owning schema, mapping, connection setup
 * or backup serialization directly.
 */
public final class SqlDataStore implements LandDataStore {
    private final BetaLandsPlugin plugin;
    private final String backend;
    private final File sqliteFile;
    private final SqlSchema schema;
    private final SqlConnectionFactory connections;
    private final SqlDataLoader loader;
    private final SqlDataWriter writer;

    public SqlDataStore(BetaLandsPlugin plugin, String backend) {
        this.plugin = plugin;
        this.backend = Text.key(backend);
        this.sqliteFile = resolveSqliteFile(
                plugin.getSettings().getSqliteFile(), plugin.getDataFolder());
        this.schema = new SqlSchema(
                plugin.getSettings().getMysqlTablePrefix());
        this.connections = new SqlConnectionFactory(this.backend, sqliteFile,
                plugin.getSettings().getMysqlHost(),
                plugin.getSettings().getMysqlPort(),
                plugin.getSettings().getMysqlDatabase(),
                plugin.getSettings().getMysqlUsername(),
                plugin.getSettings().getMysqlPassword(),
                plugin.getSettings().shouldUseMysqlSsl(),
                plugin.getSettings().getMysqlConnectTimeoutSeconds());
        this.loader = new SqlDataLoader(plugin, schema);
        this.writer = new SqlDataWriter(this.backend, schema);
    }

    SqlDataStore(String backend, String prefix, File sqliteFile) {
        this.plugin = null;
        this.backend = Text.key(backend);
        this.sqliteFile = sqliteFile;
        this.schema = new SqlSchema(prefix);
        this.connections = new SqlConnectionFactory(this.backend, sqliteFile,
                null, 3306, null, null, null, false, 5);
        this.loader = new SqlDataLoader(null, schema);
        this.writer = new SqlDataWriter(this.backend, schema);
    }

    SqlDataStore(String prefix, String host, int port, String database,
                 String username, String password, boolean useSsl,
                 int connectTimeoutSeconds) {
        this.plugin = null;
        this.backend = "mysql";
        this.sqliteFile = null;
        this.schema = new SqlSchema(prefix);
        this.connections = new SqlConnectionFactory("mysql", null, host, port,
                database, username, password, useSsl, connectTimeoutSeconds);
        this.loader = new SqlDataLoader(null, schema);
        this.writer = new SqlDataWriter(backend, schema);
    }

    public String getBackend() {
        return backend;
    }

    @Override
    public void load(LandManager manager, Map<String, PlayerData> players)
            throws IOException {
        Connection connection = null;
        try {
            connection = connections.open();
            schema.createTables(connection);
            int loadErrors = loader.load(connection, manager, players);
            if (loadErrors > 0) {
                throw new IOException("Found " + loadErrors
                        + " invalid database row(s); saving is disabled.");
            }
            writer.rememberBaseline(manager, players);
        } catch (SQLException exception) {
            throw new IOException("Could not load " + backend + " database: "
                    + exception.getMessage(), exception);
        } finally {
            close(connection);
        }
    }

    @Override
    public void save(LandManager manager, Map<String, PlayerData> players)
            throws IOException {
        saveSnapshot(LandDataSnapshot.capture(
                manager, System.currentTimeMillis()));
    }

    @Override
    public void saveSnapshot(LandDataSnapshot snapshot) throws IOException {
        Connection connection = null;
        try {
            connection = connections.open();
            schema.createTables(connection);
            writer.save(connection, snapshot);
        } catch (SQLException exception) {
            rollback(connection);
            throw new IOException("Could not save " + backend + " database: "
                    + exception.getMessage(), exception);
        } finally {
            close(connection);
        }
    }

    @Override
    public File backup(String label) throws IOException {
        if (plugin == null) {
            throw new IOException("SQL backup requires a plugin data folder");
        }
        File backupRoot = new File(plugin.getDataFolder(), "backups");
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File target = new File(backupRoot, label + "-" + timestamp);
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException(
                    "Could not create backup directory: " + target);
        }
        File dump = new File(target, "database.sql");
        Connection connection = null;
        try {
            connection = connections.open();
            schema.createTables(connection);
            SqlBackupWriter.write(connection, schema, backend, dump);
        } catch (SQLException exception) {
            throw new IOException(
                    "Could not back up " + backend + " database: "
                            + exception.getMessage(), exception);
        } finally {
            close(connection);
        }
        if ("sqlite".equals(backend) && sqliteFile.isFile()) {
            Files.copy(sqliteFile.toPath(),
                    new File(target, "betalands.db").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static File resolveSqliteFile(String configured, File dataFolder) {
        File file = configured == null || configured.trim().length() == 0
                ? new File(dataFolder, "betalands.db")
                : new File(configured);
        return file.isAbsolute() ? file : new File(dataFolder, configured);
    }

    private void rollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
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
