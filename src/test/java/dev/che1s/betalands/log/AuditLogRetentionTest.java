package dev.che1s.betalands.log;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.Test;

public class AuditLogRetentionTest {
    @Test
    public void zeroDisablesRetention() {
        assertEquals(Long.MIN_VALUE,
                AuditLogService.cutoffMillis(0, 1000000L));
    }

    @Test
    public void retentionUsesWholeDays() {
        long now = 20L * 24L * 60L * 60L * 1000L;
        assertEquals(10L * 24L * 60L * 60L * 1000L,
                AuditLogService.cutoffMillis(10, now));
    }

    @Test
    public void overflowingDurationDisablesDeletion() {
        assertEquals(Long.MIN_VALUE,
                AuditLogService.cutoffMillis(
                        Integer.MAX_VALUE, 1000000L));
    }

    @Test
    public void deletesOnlyRowsOlderThanCutoffFromBothTables() throws Exception {
        File file = File.createTempFile("betalands-audit-retention-", ".db");
        try {
            Class.forName("org.sqlite.JDBC");
            Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + file.getAbsolutePath());
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("CREATE TABLE land_logs "
                        + "(id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL)");
                statement.executeUpdate("CREATE TABLE economy_logs "
                        + "(id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL)");
                statement.executeUpdate("INSERT INTO land_logs VALUES (1, 10)");
                statement.executeUpdate("INSERT INTO land_logs VALUES (2, 30)");
                statement.executeUpdate("INSERT INTO economy_logs VALUES (1, 20)");
                statement.executeUpdate("INSERT INTO economy_logs VALUES (2, 40)");
            } finally {
                statement.close();
            }
            assertEquals(2, AuditLogService.purgeOlderThan(connection, 25L));
            assertEquals(0, AuditLogService.purgeOlderThan(connection, 25L));
            connection.close();
        } finally {
            file.delete();
        }
    }
}
