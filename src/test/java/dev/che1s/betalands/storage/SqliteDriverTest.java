package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.Test;

public class SqliteDriverTest {
    @Test
    public void bundledDriverSupportsTransactionalStyleSchema() throws Exception {
        Class.forName("org.sqlite.JDBC");
        File database = File.createTempFile("betalands-test-", ".db");
        if (!database.delete()) {
            throw new IllegalStateException("Could not prepare temporary SQLite file");
        }
        Connection connection = null;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("CREATE TABLE betalands_lands ("
                        + "id VARCHAR(64) PRIMARY KEY,"
                        + "name_key VARCHAR(255) NOT NULL UNIQUE,"
                        + "bank DOUBLE NOT NULL)");
                statement.executeUpdate(
                        "INSERT INTO betalands_lands VALUES ('id-1', 'aaa', 42.5)");
                ResultSet rows = statement.executeQuery(
                        "SELECT bank FROM betalands_lands WHERE name_key = 'aaa'");
                try {
                    assertTrue(rows.next());
                    assertEquals(42.5D, rows.getDouble(1), 0.0D);
                } finally {
                    rows.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            if (connection != null) {
                connection.close();
            }
            database.delete();
        }
    }
}
