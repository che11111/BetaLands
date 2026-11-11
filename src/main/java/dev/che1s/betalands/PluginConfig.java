package dev.che1s.betalands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.bukkit.util.config.Configuration;

public final class PluginConfig {
    private final BetaLandsPlugin plugin;
    private final File file;

    private long outsideBuildHours;
    private boolean opBypass;
    private boolean explosionsInLands;
    private boolean fireInLands;
    private boolean blockFluidCrossing;
    private boolean allowPvpByDefault;
    private boolean allowLandFlagEditing;
    private boolean allowMobSpawningByDefault;
    private boolean allowTramplingByDefault;
    private boolean allowPublicTeleportByDefault;
    private long maxMainArea;
    private long maxSubVolume;
    private int minNameLength;
    private int maxNameLength;
    private int saveIntervalSeconds;
    private String storageType;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String mysqlTablePrefix;
    private boolean mysqlUseSsl;
    private int mysqlConnectTimeoutSeconds;
    private boolean dynmapEnabled;
    private boolean bluemapEnabled;
    private boolean showSubclaims;
    private int bluemapRefreshDelaySeconds;
    private int dynmapDailyRefreshHour;
    private int dynmapDailyRefreshMinute;
    private boolean dynmapStartupRefresh;
    private String dynmapMainColor;
    private String dynmapSubclaimColor;
    private String bluemapMainColor;
    private String bluemapSubclaimColor;
    private boolean territoryMessagesEnabled;
    private boolean showWildernessMessage;
    private boolean importResidentPlots;
    private boolean economyEnabled;
    private boolean requireEconomy;
    private boolean chargeAdmins;
    private boolean adminCreatedMaintenanceExempt;
    private int maxMoneyDecimalPlaces;
    private double defaultSubclaimRent;
    private double defaultMainRent;
    private String consoleLanguage;
    private String mapLanguage;
    private boolean hideAdminLogsFromPlayers;
    private int logRetentionDays;
    private boolean bstatsEnabled;

    public PluginConfig(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() {
        ensureFile();
        ensureMissingKeys();
        Configuration configuration = new Configuration(file);
        configuration.load();
        outsideBuildHours = positiveLong(configuration, "protection.outside-build-hours", 0L);
        opBypass = bool(configuration, "protection.op-bypass", true);
        explosionsInLands = bool(configuration, "protection.explosions-in-lands", false);
        fireInLands = bool(configuration, "protection.fire-in-lands", false);
        blockFluidCrossing = bool(configuration, "protection.block-fluid-crossing", true);
        allowPvpByDefault = bool(configuration, "protection.allow-pvp-by-default", false);
        allowLandFlagEditing = bool(configuration, "protection.allow-land-flag-editing", true);
        allowMobSpawningByDefault = bool(configuration, "protection.mob-spawning", true);
        allowTramplingByDefault = bool(configuration, "protection.trampling", true);
        allowPublicTeleportByDefault = bool(configuration,
                "protection.public-teleport", true);
        maxMainArea = positiveLong(configuration, "limits.max-main-area", 1000000L);
        maxSubVolume = positiveLong(configuration, "limits.max-sub-volume", 1000000L);
        minNameLength = positiveInt(configuration, "limits.min-name-length", 2);
        maxNameLength = positiveInt(configuration, "limits.max-name-length", 24);
        saveIntervalSeconds = positiveInt(configuration, "storage.save-interval-seconds", 300);
        storageType = configuration.getString("storage.type", "sqlite");
        sqliteFile = configuration.getString("storage.sqlite.file", "betalands.db");
        mysqlHost = configuration.getString("storage.mysql.host", "127.0.0.1");
        mysqlPort = positiveInt(configuration, "storage.mysql.port", 3306);
        mysqlDatabase = configuration.getString("storage.mysql.database", "betalands");
        mysqlUsername = configuration.getString("storage.mysql.username", "root");
        mysqlPassword = configuration.getString("storage.mysql.password", "");
        mysqlTablePrefix = configuration.getString("storage.mysql.table-prefix",
                "betalands_");
        mysqlUseSsl = bool(configuration, "storage.mysql.use-ssl", false);
        mysqlConnectTimeoutSeconds = positiveInt(configuration,
                "storage.mysql.connect-timeout-seconds", 5);
        dynmapEnabled = bool(configuration, "maps.dynmap.enabled", true);
        bluemapEnabled = bool(configuration, "maps.bluemap.enabled", true);
        showSubclaims = bool(configuration, "maps.show-subclaims", true);
        int legacyMapRefreshDelay = positiveInt(configuration,
                "maps.refresh-delay-seconds", 15);
        bluemapRefreshDelaySeconds = positiveInt(configuration,
                "maps.bluemap.refresh-delay-seconds", legacyMapRefreshDelay);
        dynmapDailyRefreshHour = boundedInt(configuration,
                "maps.dynmap.daily-refresh-hour", 1, 0, 23);
        dynmapDailyRefreshMinute = boundedInt(configuration,
                "maps.dynmap.daily-refresh-minute", 0, 0, 59);
        dynmapStartupRefresh = bool(configuration,
                "maps.dynmap.startup-refresh", true);
        dynmapMainColor = configuration.getString("maps.colors.dynmap.main", "auto");
        dynmapSubclaimColor = configuration.getString("maps.colors.dynmap.subclaim", "auto");
        bluemapMainColor = configuration.getString("maps.colors.bluemap.main", "auto");
        bluemapSubclaimColor = configuration.getString("maps.colors.bluemap.subclaim", "auto");
        territoryMessagesEnabled = bool(configuration,
                "territory-messages.enabled", true);
        showWildernessMessage = bool(configuration,
                "territory-messages.show-wilderness", true);
        importResidentPlots = bool(configuration, "towny.import-resident-plots-as-subclaims", true);
        economyEnabled = bool(configuration, "economy.enabled", true);
        requireEconomy = bool(configuration, "economy.require-economy", true);
        chargeAdmins = bool(configuration, "economy.charge-admins", true);
        adminCreatedMaintenanceExempt = bool(configuration,
                "economy.admin-created-maintenance-exempt", true);
        maxMoneyDecimalPlaces = boundedInt(configuration,
                "economy.max-decimal-places", 2, 0, 2);
        defaultSubclaimRent = Math.max(0.0D,
                configuration.getDouble("economy.default-subclaim-rent", 3.00D));
        defaultMainRent = Math.max(0.0D,
                configuration.getDouble("economy.default-main-rent", 0.00D));
        consoleLanguage = configuration.getString("language.console", "auto");
        mapLanguage = configuration.getString("language.map", "auto");
        hideAdminLogsFromPlayers = bool(configuration,
                "logs.hide-admin-actions-from-players", true);
        logRetentionDays = positiveInt(configuration,
                "logs.retention-days", 0);
        bstatsEnabled = bool(configuration, "bstats.enabled", true);
    }

    private void ensureMissingKeys() {
        ConfigFileMerger.ensureMissingKeys(plugin.getClass(), file,
                "/config.yml", plugin.getServer().getLogger());
    }

    static List<String> mergeMissingKeys(List<String> defaults, List<String> currentLines) {
        return ConfigFileMerger.mergeMissingKeys(defaults, currentLines);
    }

    private void ensureFile() {
        if (file.isFile()) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getServer().getLogger().warning("Could not create plugin data directory: " + parent);
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = plugin.getClass().getResourceAsStream("/config.yml");
            if (input == null) {
                return;
            }
            output = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            plugin.getServer().getLogger().warning("Could not create config.yml: " + exception.getMessage());
        } finally {
            close(input);
            close(output);
        }
    }

    private long positiveLong(Configuration configuration, String key, long fallback) {
        int value = configuration.getInt(key, (int) Math.min(Integer.MAX_VALUE, fallback));
        return Math.max(0L, value);
    }

    private int positiveInt(Configuration configuration, String key, int fallback) {
        return Math.max(0, configuration.getInt(key, fallback));
    }

    private int boundedInt(Configuration configuration, String key,
                           int fallback, int minimum, int maximum) {
        int value = configuration.getInt(key, fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean bool(Configuration configuration, String key, boolean fallback) {
        return configuration.getBoolean(key, fallback);
    }

    private void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public long getOutsideBuildHours() {
        return outsideBuildHours;
    }

    public boolean isOpBypass() {
        return opBypass;
    }

    public boolean areExplosionsAllowedInLands() {
        return explosionsInLands;
    }

    public boolean isFireAllowedInLands() {
        return fireInLands;
    }

    public boolean shouldBlockFluidCrossing() {
        return blockFluidCrossing;
    }

    public boolean isPvpAllowedByDefault() {
        return allowPvpByDefault;
    }

    public boolean isLandFlagEditingAllowed() {
        return allowLandFlagEditing;
    }

    public boolean isMobSpawningAllowedByDefault() {
        return allowMobSpawningByDefault;
    }

    public boolean isTramplingAllowedByDefault() {
        return allowTramplingByDefault;
    }

    public boolean isPublicTeleportAllowedByDefault() {
        return allowPublicTeleportByDefault;
    }

    public long getMaxMainArea() {
        return maxMainArea;
    }

    public long getMaxSubVolume() {
        return maxSubVolume;
    }

    public int getMinNameLength() {
        return minNameLength;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public int getSaveIntervalSeconds() {
        return saveIntervalSeconds;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String getMysqlTablePrefix() {
        return mysqlTablePrefix;
    }

    public boolean shouldUseMysqlSsl() {
        return mysqlUseSsl;
    }

    public int getMysqlConnectTimeoutSeconds() {
        return mysqlConnectTimeoutSeconds;
    }

    public boolean isDynmapEnabled() {
        return dynmapEnabled;
    }

    public boolean isBluemapEnabled() {
        return bluemapEnabled;
    }

    public boolean shouldShowSubclaims() {
        return showSubclaims;
    }

    public int getBluemapRefreshDelaySeconds() {
        return bluemapRefreshDelaySeconds;
    }

    public int getDynmapDailyRefreshHour() {
        return dynmapDailyRefreshHour;
    }

    public int getDynmapDailyRefreshMinute() {
        return dynmapDailyRefreshMinute;
    }

    public boolean isDynmapStartupRefreshEnabled() {
        return dynmapStartupRefresh;
    }

    public String getDynmapMainColor() {
        return dynmapMainColor;
    }

    public String getDynmapSubclaimColor() {
        return dynmapSubclaimColor;
    }

    public String getBluemapMainColor() {
        return bluemapMainColor;
    }

    public String getBluemapSubclaimColor() {
        return bluemapSubclaimColor;
    }

    public boolean areTerritoryMessagesEnabled() {
        return territoryMessagesEnabled;
    }

    public boolean shouldShowWildernessMessage() {
        return showWildernessMessage;
    }

    public boolean shouldImportResidentPlots() {
        return importResidentPlots;
    }

    public boolean isEconomyEnabled() {
        return economyEnabled;
    }

    public boolean isEconomyRequired() {
        return requireEconomy;
    }

    public boolean shouldChargeAdmins() {
        return chargeAdmins;
    }

    public boolean shouldExemptAdminCreatedMaintenance() {
        return adminCreatedMaintenanceExempt;
    }

    public int getMaxMoneyDecimalPlaces() {
        return maxMoneyDecimalPlaces;
    }

    public double getDefaultSubclaimRent() {
        return defaultSubclaimRent;
    }

    public double getDefaultMainRent() {
        return defaultMainRent;
    }

    public String getConsoleLanguage() {
        return consoleLanguage;
    }

    public String getMapLanguage() {
        return mapLanguage;
    }

    public boolean shouldHideAdminLogsFromPlayers() {
        return hideAdminLogsFromPlayers;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public boolean isBstatsEnabled() {
        return bstatsEnabled;
    }

    public File getFile() {
        return file;
    }
}
