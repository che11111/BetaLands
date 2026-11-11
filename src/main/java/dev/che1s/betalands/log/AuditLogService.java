package dev.che1s.betalands.log;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.util.MoneyCodec;

public final class AuditLogService {
    private static final int LOG_PAGE_SIZE = 200;

    private final BetaLandsPlugin plugin;
    private final File databaseFile;
    private ExecutorService executor;
    private Connection connection;
    private volatile boolean ready;

    public AuditLogService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "logs.db");
    }

    public void initialize() {
        ready = false;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS land_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "created_at INTEGER NOT NULL,"
                        + "action TEXT NOT NULL,"
                        + "actor TEXT,"
                        + "target TEXT,"
                        + "land_id TEXT,"
                        + "land_name TEXT,"
                        + "world TEXT,"
                        + "admin_action INTEGER NOT NULL,"
                        + "details TEXT)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_land_logs_time "
                        + "ON land_logs(created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_land_logs_land_time "
                        + "ON land_logs(land_id, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_land_logs_actor_time "
                        + "ON land_logs(actor, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_land_logs_land_admin_time "
                        + "ON land_logs(land_id, admin_action, created_at)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS economy_logs ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "created_at INTEGER NOT NULL,"
                        + "action TEXT NOT NULL,"
                        + "actor TEXT,"
                        + "land_id TEXT,"
                        + "land_name TEXT,"
                        + "amount_cents BIGINT NOT NULL,"
                        + "balance_cents BIGINT,"
                        + "admin_action INTEGER NOT NULL,"
                        + "details TEXT)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_logs_time "
                        + "ON economy_logs(created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_logs_land_time "
                        + "ON economy_logs(land_id, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_logs_actor_time "
                        + "ON economy_logs(actor, created_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_logs_land_admin_time "
                        + "ON economy_logs(land_id, admin_action, created_at)");
            } finally {
                closeStatement(statement);
            }
            executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "BetaLands-AuditLog");
                    thread.setDaemon(true);
                    return thread;
                }
            });
            ready = true;
            scheduleRetention();
            plugin.getMessages().info("startup.audit-ready",
                    "Audit log database ready: {path}", "path", databaseFile.getAbsolutePath());
        } catch (Exception exception) {
            ready = false;
            plugin.getMessages().warning("startup.audit-failed",
                    "Could not initialize the audit log database: {error}",
                    "error", exception.getMessage());
        }
    }

    private void scheduleRetention() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        final int days = plugin.getSettings()
                                .getLogRetentionDays();
                        final ExecutorService current = executor;
                        if (days <= 0 || current == null
                                || current.isShutdown()) {
                            return;
                        }
                        current.execute(new Runnable() {
                            @Override
                            public void run() {
                                purgeExpiredLogs(days);
                            }
                        });
                    }
                }, 20L, 20L * 60L * 60L * 24L);
    }

    void purgeExpiredLogs(int retentionDays) {
        if (retentionDays <= 0 || !ready || connection == null) {
            return;
        }
        long cutoff = cutoffMillis(
                retentionDays, System.currentTimeMillis());
        try {
            int removed = purgeOlderThan(connection, cutoff);
            if (removed > 0) {
                plugin.getMessages().info("audit.retention-purged",
                        "Deleted {count} audit log record(s) older than {days} day(s).",
                        "count", Integer.toString(removed),
                        "days", Integer.toString(retentionDays));
            }
        } catch (SQLException exception) {
            plugin.getMessages().warning("audit.retention-failed",
                    "Could not purge expired audit logs: {error}",
                    "error", exception.getMessage());
        }
    }

    static int purgeOlderThan(Connection connection, long cutoff)
            throws SQLException {
        if (connection == null || cutoff == Long.MIN_VALUE) {
            return 0;
        }
        PreparedStatement landDelete = null;
        PreparedStatement economyDelete = null;
        try {
            landDelete = connection.prepareStatement(
                    "DELETE FROM land_logs WHERE created_at < ?");
            landDelete.setLong(1, cutoff);
            int landCount = landDelete.executeUpdate();
            economyDelete = connection.prepareStatement(
                    "DELETE FROM economy_logs WHERE created_at < ?");
            economyDelete.setLong(1, cutoff);
            return landCount + economyDelete.executeUpdate();
        } finally {
            close(economyDelete);
            close(landDelete);
        }
    }

    static long cutoffMillis(int retentionDays, long now) {
        if (retentionDays <= 0) {
            return Long.MIN_VALUE;
        }
        long duration = retentionDays * 24L * 60L * 60L * 1000L;
        long cutoff = now - duration;
        return duration <= 0L || cutoff < 0L
                ? Long.MIN_VALUE : cutoff;
    }

    public void logLand(String action, String actor, String target, Land land,
                        String details, boolean adminAction) {
        if (!ready) {
            return;
        }
        final long createdAt = System.currentTimeMillis();
        final String landId = land == null ? null : land.getId();
        final String landName = land == null ? null : land.getDisplayName();
        final String world = land == null ? null : land.getWorld();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                writeLand(createdAt, action, actor, target, landId, landName,
                        world, adminAction, details);
            }
        });
    }

    public void logEconomy(String action, String actor, Land land, double amount,
                           Double balance, String details,
                           boolean adminAction) {
        if (!ready) {
            return;
        }
        final long createdAt = System.currentTimeMillis();
        final String landId = land == null ? null : land.getId();
        final String landName = land == null ? null : land.getDisplayName();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                writeEconomy(createdAt, action, actor, landId, landName,
                        amount, balance, adminAction, details);
            }
        });
    }

    private void writeLand(long createdAt, String action, String actor, String target,
                           String landId, String landName, String world,
                           boolean adminAction, String details) {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("INSERT INTO land_logs "
                    + "(created_at, action, actor, target, land_id, land_name,"
                    + " world, admin_action, details) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setLong(1, createdAt);
            statement.setString(2, action);
            statement.setString(3, actor);
            statement.setString(4, target);
            statement.setString(5, landId);
            statement.setString(6, landName);
            statement.setString(7, world);
            statement.setInt(8, adminAction ? 1 : 0);
            statement.setString(9, details);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getMessages().warning("audit.land-write-failed",
                    "Could not write a land audit record: {error}",
                    "error", exception.getMessage());
        } finally {
            closeStatement(statement);
        }
    }

    private void writeEconomy(long createdAt, String action, String actor, String landId,
                              String landName, double amount, Double balance,
                              boolean adminAction, String details) {
        PreparedStatement statement = null;
        try {
            statement = connection.prepareStatement("INSERT INTO economy_logs "
                    + "(created_at, action, actor, land_id, land_name,"
                    + " amount_cents, balance_cents, admin_action, details) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setLong(1, createdAt);
            statement.setString(2, action);
            statement.setString(3, actor);
            statement.setString(4, landId);
            statement.setString(5, landName);
            statement.setLong(6,
                    MoneyCodec.toCents(amount));
            if (balance == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7,
                        MoneyCodec.toCents(
                                balance.doubleValue()));
            }
            statement.setInt(8, adminAction ? 1 : 0);
            statement.setString(9, details);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getMessages().warning("audit.economy-write-failed",
                    "Could not write an economy audit record: {error}",
                    "error", exception.getMessage());
        } finally {
            closeStatement(statement);
        }
    }

    public List<String> readRecent(int limit, String playerFilter) {
        return readRecent(limit, 0L, playerFilter, true);
    }

    public boolean readRecentAsync(final int limit, final String playerFilter,
                                   final Consumer<List<String>> callback) {
        ExecutorService current = executor;
        if (!ready || current == null || current.isShutdown() || callback == null) {
            return false;
        }
        current.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(readRecent(limit, playerFilter, false));
            }
        });
        return true;
    }

    public boolean readRecentPageAsync(final int page,
                                       final String playerFilter,
                                       final Consumer<List<String>> callback) {
        ExecutorService current = executor;
        if (!ready || current == null || current.isShutdown()
                || page < 1 || callback == null) {
            return false;
        }
        current.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(readRecent(LOG_PAGE_SIZE,
                        pageOffset(page), playerFilter, false));
            }
        });
        return true;
    }

    private List<String> readRecent(int limit, String playerFilter, boolean waitForWrites) {
        return readRecent(limit, 0L, playerFilter, waitForWrites);
    }

    private List<String> readRecent(int limit, long offset, String playerFilter,
                                    boolean waitForWrites) {
        List<String> result = new ArrayList<String>();
        if (!ready) {
            result.add("[BetaLands] Audit log database is unavailable.");
            return result;
        }
        if (waitForWrites) {
            flushQueuedWrites();
        }
        int safeLimit = Math.max(1, Math.min(200, limit));
        String filter = playerFilter == null || playerFilter.length() == 0
                ? null : "%" + playerFilter + "%";
        Connection readConnection = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            readConnection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            String whereLand = filter == null ? "1=1" : "(actor LIKE ? OR target LIKE ?)";
            String whereEconomy = filter == null ? "1=1" : "actor LIKE ?";
            String sql = "SELECT created_at, category, action, actor, target, land_name, "
                    + "details, amount_cents, balance_cents FROM ("
                    + "SELECT id AS row_id, created_at, 'LAND' AS category, action, actor, target, "
                    + "land_name, details, NULL AS amount_cents, NULL AS balance_cents FROM land_logs WHERE "
                    + whereLand
                    + " UNION ALL "
                    + "SELECT id AS row_id, created_at, 'ECONOMY' AS category, action, actor, NULL AS target, "
                    + "land_name, details, amount_cents, balance_cents FROM economy_logs WHERE "
                    + whereEconomy
                    + ") ORDER BY created_at DESC, category DESC, row_id DESC "
                    + "LIMIT ? OFFSET ?";
            statement = readConnection.prepareStatement(sql);
            int index = 1;
            if (filter != null) {
                statement.setString(index++, filter);
                statement.setString(index++, filter);
                statement.setString(index++, filter);
            }
            statement.setInt(index, safeLimit);
            statement.setLong(index + 1, Math.max(0L, offset));
            rows = statement.executeQuery();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rows.next()) {
                StringBuilder line = new StringBuilder();
                line.append('[').append(format.format(new Date(rows.getLong("created_at")))).append("] ");
                line.append(rows.getString("category")).append(' ');
                line.append(rows.getString("action"));
                String actor = rows.getString("actor");
                if (actor != null) {
                    line.append(" actor=").append(actor);
                }
                String target = rows.getString("target");
                if (target != null) {
                    line.append(" target=").append(target);
                }
                String landName = rows.getString("land_name");
                if (landName != null) {
                    line.append(" land=").append(landName);
                }
                if ("ECONOMY".equals(rows.getString("category"))) {
                    line.append(" amount=").append(plugin.getEconomy().format(
                            MoneyCodec.fromCents(
                                    rows.getLong("amount_cents"))));
                    double balance =
                            MoneyCodec.fromCents(
                                    rows.getLong("balance_cents"));
                    if (!rows.wasNull()) {
                        line.append(" balance=").append(plugin.getEconomy().format(balance));
                    }
                }
                String details = rows.getString("details");
                if (details != null && details.length() > 0) {
                    line.append(" ").append(details);
                }
                result.add(line.toString());
            }
        } catch (SQLException exception) {
            result.add("[BetaLands] Could not read audit logs: " + exception.getMessage());
        } finally {
            closeResult(rows);
            closeStatement(statement);
            closeConnection(readConnection);
        }
        Collections.reverse(result);
        return result;
    }

    public List<String> readLandRecent(Land land, int limit) {
        return readLandRecent(land == null ? null : land.getId(),
                limit, 0L, true, true);
    }

    public boolean readLandRecentAsync(final Land land, final int limit,
                                       final boolean includeAdminActions,
                                       final Consumer<List<String>> callback) {
        ExecutorService current = executor;
        if (!ready || current == null || current.isShutdown() || callback == null) {
            return false;
        }
        final String landId = land == null ? null : land.getId();
        current.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(readLandRecent(landId, limit, 0L,
                        includeAdminActions, false));
            }
        });
        return true;
    }

    public boolean readLandRecentPageAsync(final Land land, final int page,
                                           final Consumer<List<String>> callback) {
        ExecutorService current = executor;
        if (!ready || current == null || current.isShutdown()
                || page < 1 || callback == null) {
            return false;
        }
        final String landId = land == null ? null : land.getId();
        current.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(readLandRecent(landId, LOG_PAGE_SIZE,
                        pageOffset(page), true, false));
            }
        });
        return true;
    }

    private List<String> readLandRecent(String landId, int limit, boolean waitForWrites) {
        return readLandRecent(landId, limit, 0L, true, waitForWrites);
    }

    private List<String> readLandRecent(String landId, int limit, long offset,
                                        boolean includeAdminActions,
                                        boolean waitForWrites) {
        List<String> result = new ArrayList<String>();
        if (!ready || landId == null) {
            result.add("[BetaLands] Audit log database unavailable or land not found.");
            return result;
        }
        if (waitForWrites) {
            flushQueuedWrites();
        }
        int safeLimit = Math.max(1, Math.min(200, limit));
        Connection readConnection = null;
        PreparedStatement statement = null;
        ResultSet rows = null;
        try {
            readConnection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            String adminFilter = includeAdminActions
                    ? "" : " AND admin_action=0";
            String sql = "SELECT created_at, category, action, actor, target, details, "
                    + "amount_cents, balance_cents FROM ("
                    + "SELECT id AS row_id, created_at, 'LAND' AS category, action, actor, target, "
                    + "details, NULL AS amount_cents, NULL AS balance_cents FROM land_logs WHERE land_id = ?"
                    + adminFilter
                    + "UNION ALL "
                    + "SELECT id AS row_id, created_at, 'ECONOMY' AS category, action, actor, NULL AS target, "
                    + "details, amount_cents, balance_cents FROM economy_logs WHERE land_id = ?"
                    + adminFilter
                    + ") ORDER BY created_at DESC, category DESC, row_id DESC "
                    + "LIMIT ? OFFSET ?";
            statement = readConnection.prepareStatement(sql);
            statement.setString(1, landId);
            statement.setString(2, landId);
            statement.setInt(3, safeLimit);
            statement.setLong(4, Math.max(0L, offset));
            rows = statement.executeQuery();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            while (rows.next()) {
                result.add(formatAuditRow(rows, format));
            }
        } catch (SQLException exception) {
            result.add("[BetaLands] Could not read land logs: " + exception.getMessage());
        } finally {
            closeResult(rows);
            closeStatement(statement);
            closeConnection(readConnection);
        }
        Collections.reverse(result);
        return result;
    }

    private static long pageOffset(int page) {
        return (long) (Math.max(1, page) - 1) * LOG_PAGE_SIZE;
    }

    private String formatAuditRow(ResultSet rows, SimpleDateFormat format) throws SQLException {
        StringBuilder line = new StringBuilder();
        line.append('[').append(format.format(new Date(rows.getLong("created_at")))).append("] ");
        line.append(rows.getString("category")).append(' ');
        line.append(rows.getString("action"));
        String actor = rows.getString("actor");
        if (actor != null) {
            line.append(" actor=").append(actor);
        }
        String target = rows.getString("target");
        if (target != null) {
            line.append(" target=").append(target);
        }
        if ("ECONOMY".equals(rows.getString("category"))) {
        line.append(" amount=").append(plugin.getEconomy().format(
                MoneyCodec.fromCents(
                        rows.getLong("amount_cents"))));
        double balance =
                MoneyCodec.fromCents(
                        rows.getLong("balance_cents"));
            if (!rows.wasNull()) {
                line.append(" balance=").append(plugin.getEconomy().format(balance));
            }
        }
        String details = rows.getString("details");
        if (details != null && details.length() > 0) {
            line.append(" ").append(details);
        }
        return line.toString();
    }

    public void disable() {
        ready = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        closeConnection(connection);
        connection = null;
    }

    private void flushQueuedWrites() {
        if (executor == null) {
            return;
        }
        Future<?> barrier = executor.submit(new Runnable() {
            @Override
            public void run() {
                // Barrier task: all previously queued inserts have completed.
            }
        });
        try {
            barrier.get(5L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            barrier.cancel(true);
        }
    }

    private void closeStatement(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private static void close(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void closeResult(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
