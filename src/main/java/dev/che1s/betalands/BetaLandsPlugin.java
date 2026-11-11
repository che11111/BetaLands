package dev.che1s.betalands;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import dev.che1s.betalands.api.BetaLandsApi;
import dev.che1s.betalands.api.BetaLandsApiImpl;
import dev.che1s.betalands.command.BetaLandsCommand;
import dev.che1s.betalands.economy.EconomyService;
import dev.che1s.betalands.integration.MapService;
import dev.che1s.betalands.lang.MessageService;
import dev.che1s.betalands.levels.LevelService;
import dev.che1s.betalands.listener.ProtectionListener;
import dev.che1s.betalands.log.AuditLogService;
import dev.che1s.betalands.metrics.BukkitMetrics;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.service.LandFeeNotificationService;
import dev.che1s.betalands.service.LandMaintenanceService;
import dev.che1s.betalands.service.SelectionManager;
import dev.che1s.betalands.storage.LandDataStore;
import dev.che1s.betalands.storage.LandDataSnapshot;
import dev.che1s.betalands.storage.StorageManager;

public final class BetaLandsPlugin extends JavaPlugin {
    private PluginConfig settings;
    private LandDataStore dataStore;
    private StorageManager storageManager;
    private LandManager landManager;
    private SelectionManager selections;
    private MapService mapService;
    private EconomyService economy;
    private LevelService levels;
    private LandMaintenanceService maintenanceService;
    private LandFeeNotificationService feeNotifications;
    private AuditLogService auditLog;
    private MessageService messages;
    private BetaLandsApi api;
    private BukkitMetrics metrics;
    private boolean dataLoaded;
    private final Object mapRefreshLock = new Object();
    private int bluemapRefreshTaskId = -1;
    private int dynmapRefreshTaskId = -1;
    private final Object storageSaveLock = new Object();
    private ExecutorService storageExecutor;
    private LandDataSnapshot pendingStorageSnapshot;
    private boolean pendingStorageLogFailure;
    private boolean storageSaveRunning;

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getServer().getLogger().severe("Could not create plugin data directory: " + dataFolder);
        }

        settings = new PluginConfig(this);
        settings.load();
        messages = new MessageService(this);
        messages.load();
        startMetrics();
        if (getServer().getPluginManager().isPluginEnabled("Towny")) {
            messages.info("startup.towny-hook",
                    "Detected Towny {version}; live Towny migration data is available.",
                    "version", getServer().getPluginManager().getPlugin("Towny")
                            .getDescription().getVersion());
        }
        auditLog = new AuditLogService(this);
        auditLog.initialize();
        levels = new LevelService(this);
        levels.load();
        messages.info("startup.levels-ready", "Loaded {count} land levels.",
                "count", Integer.toString(levels.getProfiles().size()));
        economy = new EconomyService(this);
        economy.initialize();
        selections = new SelectionManager();
        storageManager = new StorageManager(this);
        dataStore = storageManager;
        storageExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "BetaLands-Storage");
                thread.setDaemon(true);
                return thread;
            }
        });
        landManager = new LandManager(this);
        try {
            dataStore.load(landManager, landManager.getPlayers());
            dataLoaded = true;
        } catch (IOException exception) {
            dataLoaded = false;
            messages.severe("data.load-failed", "Could not load BetaLands data: {error}",
                    "error", exception.getMessage());
            messages.severe("data.read-only",
                    "BetaLands is running in read-only mode to protect existing files.");
        }

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        BetaLandsCommand commandExecutor = new BetaLandsCommand(this);
        PluginCommand command = getCommand("land");
        if (command != null) {
            command.setExecutor(commandExecutor);
        } else {
            messages.severe("startup.command-missing",
                    "Command land is missing from plugin.yml.");
        }

        for (Player player : getServer().getOnlinePlayers()) {
            landManager.startSession(player);
            landManager.syncPlayerLevel(player);
        }

        mapService = new MapService(this);
        mapService.enable();
        schedulePeriodicSave();
        maintenanceService = new LandMaintenanceService(this);
        feeNotifications = new LandFeeNotificationService(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (levels.refreshExpiredGrants() > 0) {
                    requestSaveAsync(true);
                }
                maintenanceService.runMaintenance();
                feeNotifications.runMinuteCheck();
            }
        }, 100L, 1200L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (levels.refreshExpiredGrants() > 0) {
                    requestSaveAsync(true);
                }
            }
        }, 20L, 100L);
        api = new BetaLandsApiImpl(this);
        getServer().getServicesManager().register(
                BetaLandsApi.class, api, this, ServicePriority.Normal);

        messages.info("startup.ready",
                "BetaLands {version} enabled with {lands} lands. Storage={storage}. Outside-build-hours={hours}.",
                "version", getDescription().getVersion(),
                "lands", Integer.toString(landManager.getLands().size()),
                "storage", storageManager.getBackend(),
                "hours", Long.toString(settings.getOutsideBuildHours()));
    }

    private void schedulePeriodicSave() {
        long ticks = Math.max(20L, (long) settings.getSaveIntervalSeconds() * 20L);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                requestSaveAsync(true);
            }
        }, ticks, ticks);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (api != null) {
            getServer().getServicesManager().unregister(BetaLandsApi.class, api);
            api = null;
        }
        if (landManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                landManager.stopSession(player);
            }
            landManager.settleAllSessions();
            requestSaveAsync(false);
            shutdownStorageExecutor();
        }
        if (mapService != null) {
            mapService.disable();
        }
        if (auditLog != null) {
            auditLog.disable();
        }
        messages.info("shutdown.stopping", "BetaLands disabling.");
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    private void startMetrics() {
        if (!settings.isBstatsEnabled()) {
            return;
        }
        try {
            metrics = new BukkitMetrics(this, 35249);
        } catch (Exception | LinkageError exception) {
            messages.warning("startup.bstats-failed",
                    "Could not initialize bStats: {error}",
                    "error", exception.getMessage());
        }
    }

    public boolean requestSaveAsync(boolean logFailure) {
        if (!dataLoaded || landManager == null || dataStore == null) {
            return false;
        }
        synchronized (storageSaveLock) {
            pendingStorageSnapshot = LandDataSnapshot.capture(
                    landManager, System.currentTimeMillis());
            pendingStorageLogFailure = logFailure;
            if (storageSaveRunning || storageExecutor == null
                    || storageExecutor.isShutdown()) {
                return storageExecutor != null && !storageExecutor.isShutdown();
            }
            storageSaveRunning = true;
            try {
                storageExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        runStorageSaveWorker();
                    }
                });
            } catch (RuntimeException exception) {
                storageSaveRunning = false;
                pendingStorageSnapshot = null;
                return false;
            }
        }
        return true;
    }

    /**
     * Persists the latest in-memory state and then runs a storage-only task on the
     * serial storage executor. The task never runs concurrently with database saves.
     */
    public boolean runStorageTaskAfterSave(final Runnable task,
                                           final Consumer<Throwable> failure) {
        if (task == null || !dataLoaded || landManager == null || dataStore == null) {
            return false;
        }
        final LandDataSnapshot snapshot;
        synchronized (storageSaveLock) {
            if (storageExecutor == null || storageExecutor.isShutdown()) {
                return false;
            }
            snapshot = LandDataSnapshot.capture(
                    landManager, System.currentTimeMillis());
            try {
                storageExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dataStore.saveSnapshot(snapshot);
                            task.run();
                        } catch (Exception exception) {
                            if (failure != null) {
                                failure.accept(exception);
                            } else {
                                messages.severe("data.async-task-failed",
                                        "Background storage task failed: {error}",
                                        "error", exception.getMessage());
                            }
                        }
                    }
                });
            } catch (RuntimeException exception) {
                return false;
            }
        }
        return true;
    }

    private void runStorageSaveWorker() {
        while (true) {
            LandDataSnapshot snapshot;
            boolean logFailure;
            synchronized (storageSaveLock) {
                snapshot = pendingStorageSnapshot;
                logFailure = pendingStorageLogFailure;
                pendingStorageSnapshot = null;
                if (snapshot == null) {
                    storageSaveRunning = false;
                    return;
                }
            }
            try {
                dataStore.saveSnapshot(snapshot);
            } catch (Exception exception) {
                if (logFailure) {
                    messages.severe("data.save-failed",
                            "Could not save BetaLands data: {error}",
                            "error", exception.getMessage());
                }
            }
        }
    }

    private void shutdownStorageExecutor() {
        ExecutorService executor;
        synchronized (storageSaveLock) {
            executor = storageExecutor;
            storageExecutor = null;
        }
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                messages.severe("data.async-save-timeout",
                        "Timed out while waiting for the final storage save.");
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void reloadPlugin() {
        settings.load();
        messages.load();
        levels.load();
        economy.initialize();
        for (Player player : getServer().getOnlinePlayers()) {
            landManager.syncPlayerLevel(player);
        }
        cancelMapRefreshes();
        if (mapService != null) {
            mapService.disable();
            mapService = null;
        }
        if (settings.isDynmapEnabled() || settings.isBluemapEnabled()) {
            mapService = new MapService(this);
            mapService.enable();
        }
    }

    public PluginConfig getSettings() {
        return settings;
    }

    public LandDataStore getDataStore() {
        return dataStore;
    }

    public LandManager getLandManager() {
        return landManager;
    }

    public SelectionManager getSelections() {
        return selections;
    }

    public MapService getMapService() {
        return mapService;
    }

    public EconomyService getEconomy() {
        return economy;
    }

    public LevelService getLevels() {
        return levels;
    }

    public LandMaintenanceService getMaintenanceService() {
        return maintenanceService;
    }

    public LandFeeNotificationService getFeeNotifications() {
        return feeNotifications;
    }

    public AuditLogService getAuditLog() {
        return auditLog;
    }

    public MessageService getMessages() {
        return messages;
    }

    public BetaLandsApi getApi() {
        return api;
    }

    public File getPluginJarFile() {
        return getFile();
    }

    public void scheduleMapRefresh() {
        scheduleBluemapRefresh();
        scheduleDynmapDailyRefresh();
    }

    public void scheduleBluemapRefresh() {
        if (!settings.isBluemapEnabled()) {
            return;
        }
        long ticks = Math.max(0L,
                (long) settings.getBluemapRefreshDelaySeconds() * 20L);
        scheduleBluemapRefreshAfter(ticks);
    }

    public void scheduleBluemapRefreshAfter(long ticks) {
        scheduleBluemapRefreshAfter(ticks, false);
    }

    private void scheduleBluemapRefreshAfter(long ticks, boolean replace) {
        if (!settings.isBluemapEnabled()) {
            return;
        }
        synchronized (mapRefreshLock) {
            if (mapService == null) {
                return;
            }
            if (bluemapRefreshTaskId != -1 && !replace) {
                return;
            }
            if (bluemapRefreshTaskId != -1) {
                getServer().getScheduler().cancelTask(bluemapRefreshTaskId);
                bluemapRefreshTaskId = -1;
            }
            bluemapRefreshTaskId = getServer().getScheduler()
                    .scheduleSyncDelayedTask(this, new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mapRefreshLock) {
                                bluemapRefreshTaskId = -1;
                            }
                            if (mapService != null) {
                                long started = System.nanoTime();
                                mapService.refreshBlueMap();
                                logSlowRefresh(started);
                            }
                        }
                    }, Math.max(0L, ticks));
        }
    }

    public void refreshBluemapNow() {
        scheduleBluemapRefreshAfter(1L, true);
    }

    public void scheduleDynmapDailyRefresh() {
        if (!settings.isDynmapEnabled()) {
            return;
        }
        synchronized (mapRefreshLock) {
            if (mapService == null || dynmapRefreshTaskId != -1) {
                return;
            }
            dynmapRefreshTaskId = getServer().getScheduler()
                    .scheduleSyncDelayedTask(this, new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mapRefreshLock) {
                                dynmapRefreshTaskId = -1;
                            }
                            if (mapService != null) {
                                long started = System.nanoTime();
                                mapService.refreshDynmap();
                                logSlowRefresh(started);
                            }
                        }
                    }, ticksUntilNextDynmapRefresh());
        }
    }

    public void scheduleDynmapRefreshAfter(long ticks) {
        if (!settings.isDynmapEnabled()) {
            return;
        }
        synchronized (mapRefreshLock) {
            if (mapService == null) {
                return;
            }
            if (dynmapRefreshTaskId != -1) {
                getServer().getScheduler().cancelTask(dynmapRefreshTaskId);
                dynmapRefreshTaskId = -1;
            }
            dynmapRefreshTaskId = getServer().getScheduler()
                    .scheduleSyncDelayedTask(this, new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mapRefreshLock) {
                                dynmapRefreshTaskId = -1;
                            }
                            if (mapService != null) {
                                long started = System.nanoTime();
                                mapService.refreshDynmap();
                                logSlowRefresh(started);
                            }
                        }
                    }, Math.max(0L, ticks));
        }
    }

    public void scheduleDynmapStartupRefresh() {
        scheduleDynmapRefreshAfter(100L);
    }

    public void refreshDynmapNow() {
        scheduleDynmapRefreshAfter(1L);
    }

    public void rescheduleMapRefreshes() {
        cancelMapRefreshes();
        scheduleMapRefresh();
    }

    public void cancelMapRefreshes() {
        synchronized (mapRefreshLock) {
            if (bluemapRefreshTaskId != -1) {
                getServer().getScheduler().cancelTask(bluemapRefreshTaskId);
                bluemapRefreshTaskId = -1;
            }
            if (dynmapRefreshTaskId != -1) {
                getServer().getScheduler().cancelTask(dynmapRefreshTaskId);
                dynmapRefreshTaskId = -1;
            }
        }
    }

    long ticksUntilNextDynmapRefresh() {
        return ticksUntilNextRefresh(settings.getDynmapDailyRefreshHour(),
                settings.getDynmapDailyRefreshMinute(), Calendar.getInstance());
    }

    static long ticksUntilNextRefresh(int hour, int minute, Calendar now) {
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        next.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        long millis = Math.max(0L,
                next.getTimeInMillis() - now.getTimeInMillis());
        return (millis + 49L) / 50L;
    }

    private void logSlowRefresh(long started) {
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        if (elapsedMillis >= 100L) {
            messages.warning("maps.refresh-slow",
                    "Map marker refresh took {milliseconds} ms.",
                    "milliseconds", Long.toString(elapsedMillis));
        }
    }
}
