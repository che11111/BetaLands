package dev.che1s.betalands.storage;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.service.LandManager;

public final class StorageManager implements LandDataStore {
    private final BetaLandsPlugin plugin;
    private final DataStore tsv;
    private final String backend;
    private final SqlDataStore sql;

    public StorageManager(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.tsv = new DataStore(plugin);
        String configured = plugin.getSettings().getStorageType();
        String normalized = configured == null ? "sqlite"
                : configured.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"mysql".equals(normalized) && !"tsv".equals(normalized)) {
            normalized = "sqlite";
        }
        this.backend = normalized;
        this.sql = "tsv".equals(backend) ? null : new SqlDataStore(plugin, backend);
    }

    @Override
    public void load(LandManager manager, Map<String, PlayerData> players) throws IOException {
        if ("tsv".equals(backend)) {
            tsv.load(manager, players);
            return;
        }
        sql.load(manager, players);
    }

    @Override
    public void save(LandManager manager, Map<String, PlayerData> players) throws IOException {
        saveSnapshot(LandDataSnapshot.capture(manager, System.currentTimeMillis()));
    }

    @Override
    public void saveSnapshot(LandDataSnapshot snapshot) throws IOException {
        if (sql == null) {
            tsv.saveSnapshot(snapshot);
        } else {
            sql.saveSnapshot(snapshot);
        }
    }

    @Override
    public File backup(String label) throws IOException {
        return sql == null ? tsv.backup(label) : sql.backup(label);
    }

    public String getBackend() {
        return backend;
    }

}
