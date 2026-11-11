package dev.che1s.betalands.integration;

import org.bukkit.plugin.Plugin;

import dev.che1s.betalands.BetaLandsPlugin;

public final class MapService {
    private static final long DYNMAP_FAST_RETRY_TICKS = 40L;
    private static final long DYNMAP_SLOW_RETRY_TICKS = 600L;
    private static final int DYNMAP_FAST_RETRY_LIMIT = 25;

    private final BetaLandsPlugin plugin;
    private DynmapIntegration dynmap;
    private BlueMapIntegration blueMap;
    private int dynmapUnavailableChecks;
    private boolean dynmapUnavailableReported;
    private String dynmapHookState = "not-checked";
    private String blueMapHookState = "not-checked";

    public MapService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!plugin.getSettings().isDynmapEnabled()
                && !plugin.getSettings().isBluemapEnabled()) {
            dynmapHookState = "disabled-in-config";
            blueMapHookState = "disabled-in-config";
            plugin.getMessages().info("startup.map-disabled",
                    "Both map integrations are disabled in config.yml.");
            return;
        }
        if (plugin.getSettings().isDynmapEnabled()) {
            ensureDynmapHook();
            if (plugin.getSettings().isDynmapStartupRefreshEnabled()) {
                plugin.scheduleDynmapStartupRefresh();
            } else {
                plugin.scheduleDynmapDailyRefresh();
            }
        } else {
            dynmapHookState = "disabled-in-config";
        }
        if (plugin.getSettings().isBluemapEnabled()) {
            ensureBlueMapHook();
            plugin.scheduleBluemapRefresh();
        } else {
            blueMapHookState = "disabled-in-config";
        }
    }

    public void refresh() {
        refreshDynmap();
        refreshBlueMap();
    }

    public void refreshDynmap() {
        if (!plugin.getSettings().isDynmapEnabled()) {
            return;
        }
        boolean dynmapReady = false;
        ensureDynmapHook();
        if (dynmap != null) {
            try {
                dynmapReady = dynmap.refresh();
                if (dynmapReady) {
                    dynmapUnavailableChecks = 0;
                    dynmapUnavailableReported = false;
                }
            } catch (RuntimeException | LinkageError exception) {
                dynmapReady = false;
                plugin.getMessages().warning("maps.dynmap-refresh-failed",
                        "Dynmap marker refresh failed: {error}", "error", exception.getMessage());
                plugin.getServer().getLogger().warning(
                        "[BetaLands] Dynmap marker refresh failed: " + exception);
            }
        }
        if (dynmapReady) {
            plugin.scheduleDynmapDailyRefresh();
        } else {
            scheduleDynmapRetry();
        }
    }

    public void refreshBlueMap() {
        if (!plugin.getSettings().isBluemapEnabled()) {
            return;
        }
        ensureBlueMapHook();
        if (blueMap != null) {
            try {
                blueMap.refresh();
            } catch (RuntimeException exception) {
                plugin.getMessages().warning("maps.bluemap-refresh-failed",
                        "BlueMap marker refresh failed: {error}", "error", exception.getMessage());
            }
        }
    }

    public void disable() {
        if (dynmap != null) {
            dynmap.disable();
        }
        if (blueMap != null) {
            blueMap.disable();
        }
        dynmap = null;
        blueMap = null;
        dynmapUnavailableChecks = 0;
        dynmapUnavailableReported = false;
        dynmapHookState = "not-checked";
        blueMapHookState = "not-checked";
    }

    public String dynmapDiagnostic() {
        return dynmap == null ? "not-hooked(" + dynmapHookState + ")" : dynmap.diagnostic();
    }

    public String blueMapDiagnostic() {
        return blueMap == null ? "not-hooked(" + blueMapHookState + ")" : blueMap.diagnostic();
    }

    private boolean ensureDynmapHook() {
        if (dynmap != null) {
            return true;
        }
        Plugin dynmapPlugin = findPlugin("dynmap");
        if (dynmapPlugin == null) {
            dynmapHookState = "plugin-not-found";
            return false;
        }
        if (!dynmapPlugin.isEnabled()) {
            dynmapHookState = "plugin-disabled";
            return false;
        }
        try {
            dynmap = new DynmapIntegration(plugin, dynmapPlugin);
            dynmapHookState = "hooked";
            plugin.getMessages().info("startup.dynmap-hook",
                    "Connected to Dynmap; waiting for its marker API.");
            return true;
        } catch (RuntimeException | LinkageError exception) {
            dynmapHookState = "hook-failed: " + exception;
            plugin.getMessages().warning("startup.dynmap-failed",
                    "Could not hook Dynmap: {error}", "error", exception.getMessage());
            plugin.getServer().getLogger().warning(
                    "[BetaLands] Dynmap integration failed: " + exception);
            return false;
        }
    }

    private Plugin findPlugin(String name) {
        Plugin direct = plugin.getServer().getPluginManager().getPlugin(name);
        if (direct != null) {
            return direct;
        }
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            if (candidate != null && candidate.getDescription() != null
                    && name.equalsIgnoreCase(candidate.getDescription().getName())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean ensureBlueMapHook() {
        if (blueMap != null) {
            return true;
        }
        Plugin blueMapPlugin = findPlugin("BlueMap");
        if (blueMapPlugin == null) {
            blueMapHookState = "plugin-not-found";
            return false;
        }
        if (!blueMapPlugin.isEnabled()) {
            blueMapHookState = "plugin-disabled";
            return false;
        }
        try {
            blueMap = new BlueMapIntegration(plugin);
            blueMapHookState = "hooked";
            plugin.getMessages().info("startup.bluemap-hook",
                    "Hooked BlueMap marker API.");
            return true;
        } catch (RuntimeException | LinkageError exception) {
            blueMapHookState = "hook-failed: " + exception;
            plugin.getMessages().warning("startup.bluemap-failed",
                    "Could not hook BlueMap: {error}", "error", exception.getMessage());
            return false;
        }
    }

    private void scheduleDynmapRetry() {
        dynmapUnavailableChecks++;
        if (!dynmapUnavailableReported && dynmapUnavailableChecks >= 5) {
            dynmapUnavailableReported = true;
            if (dynmap == null) {
                plugin.getMessages().warning("maps.dynmap-plugin-unavailable",
                        "Dynmap is not available for hooking ({state}). "
                                + "BetaLands will keep checking.",
                        "state", dynmapHookState);
            } else {
                plugin.getMessages().warning("maps.dynmap-api-waiting",
                        "Dynmap is installed, but its Marker API is not initialized. "
                                + "Enable org.dynmap.MarkersComponent in Dynmap's configuration.txt. "
                                + "BetaLands will keep checking.");
            }
        }
        long retryTicks = dynmapUnavailableChecks <= DYNMAP_FAST_RETRY_LIMIT
                ? DYNMAP_FAST_RETRY_TICKS : DYNMAP_SLOW_RETRY_TICKS;
        plugin.scheduleDynmapRefreshAfter(retryTicks);
    }
}
