package dev.che1s.betalands.storage;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.service.LandManager;

public interface LandDataStore {
    void load(LandManager manager, Map<String, PlayerData> players) throws IOException;

    void save(LandManager manager, Map<String, PlayerData> players) throws IOException;

    void saveSnapshot(LandDataSnapshot snapshot) throws IOException;

    File backup(String label) throws IOException;
}
