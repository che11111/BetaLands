package dev.che1s.betalands.storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Produces portable SQL dumps independent of the active save format.
 */
final class SqlBackupWriter {
    private SqlBackupWriter() {
    }

    static void write(Connection connection, SqlSchema schema,
                      String backend, File target) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(
                target.toPath(), StandardCharsets.UTF_8);
        try {
            writer.write("-- BetaLands " + backend + " backup");
            writer.newLine();
            for (String table : new String[]{"lands", "areas", "members",
                    "rent_accounts", "players", "metadata"}) {
                writeTable(connection, schema, writer, table);
            }
        } catch (SQLException exception) {
            throw new IOException(
                    "Could not write SQL backup: " + exception.getMessage(),
                    exception);
        } finally {
            close(writer);
        }
    }

    private static void writeTable(Connection connection, SqlSchema schema,
                                   BufferedWriter writer, String table)
            throws SQLException, IOException {
        Statement statement = connection.createStatement();
        ResultSet rows = null;
        try {
            rows = statement.executeQuery(
                    "SELECT * FROM " + schema.table(table));
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                writer.write("INSERT INTO " + schema.table(table) + " (");
                for (int column = 1; column <= metadata.getColumnCount();
                     column++) {
                    if (column > 1) {
                        writer.write(", ");
                    }
                    writer.write(metadata.getColumnLabel(column));
                }
                writer.write(") VALUES (");
                for (int column = 1; column <= metadata.getColumnCount();
                     column++) {
                    if (column > 1) {
                        writer.write(", ");
                    }
                    writer.write(sqlValue(rows.getObject(column)));
                }
                writer.write(");");
                writer.newLine();
            }
        } finally {
            close(rows);
            close(statement);
        }
    }

    private static String sqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static void close(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
