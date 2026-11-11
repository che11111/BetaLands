package dev.che1s.betalands.storage;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import dev.che1s.betalands.util.Text;

/**
 * Opens SQLite and MySQL connections with the legacy runtime's JDBC drivers.
 */
final class SqlConnectionFactory {
    private final String backend;
    private final File sqliteFile;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final boolean mysqlUseSsl;
    private final int mysqlConnectTimeoutSeconds;

    SqlConnectionFactory(String backend, File sqliteFile, String mysqlHost,
                         int mysqlPort, String mysqlDatabase,
                         String mysqlUsername, String mysqlPassword,
                         boolean mysqlUseSsl, int mysqlConnectTimeoutSeconds) {
        this.backend = Text.key(backend);
        this.sqliteFile = sqliteFile;
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.mysqlUseSsl = mysqlUseSsl;
        this.mysqlConnectTimeoutSeconds =
                Math.max(1, mysqlConnectTimeoutSeconds);
    }

    Connection open() throws SQLException, IOException {
        if ("sqlite".equals(backend)) {
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create SQLite directory: " + parent);
            }
            loadDriver("org.sqlite.JDBC", "SQLite");
            Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + sqliteFile.getAbsolutePath());
            Statement statement = connection.createStatement();
            try {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA foreign_keys=ON");
            } finally {
                close(statement);
            }
            return connection;
        }
        loadDriver("com.mysql.jdbc.Driver", "MySQL",
                "com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/"
                + mysqlDatabase + "?useUnicode=true&characterEncoding=UTF-8&useSSL="
                + mysqlUseSsl + "&connectTimeout="
                + mysqlConnectTimeoutSeconds * 1000;
        return DriverManager.getConnection(url, mysqlUsername, mysqlPassword);
    }

    private void loadDriver(String... classes) throws IOException {
        for (String className : classes) {
            try {
                Class.forName(className);
                return;
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new IOException("JDBC driver is not available for " + backend);
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
