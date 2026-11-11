package dev.che1s.betalands.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.util.Text;

/**
 * Owns player play-time sessions and level snapshots.
 */
public final class PlayerSessionService {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final Map<String, PlayerData> players = new LinkedHashMap<String, PlayerData>();

    public PlayerSessionService(BetaLandsPlugin plugin, LandManager lands) {
        this.plugin = plugin;
        this.lands = lands;
    }

    public Map<String, PlayerData> getPlayers() {
        return players;
    }

    public void clear() {
        players.clear();
    }

    public void syncPlayerLevel(Player player) {
        if (player == null || plugin.getLevels() == null) {
            return;
        }
        LevelProfile profile = plugin.getLevels().getProfile(player);
        PlayerData data = getOrCreatePlayerData(player);
        data.setLevelId(profile.getId());
        applyLevelToOwnedLands(player.getName(), profile);
    }

    public void applyLevelToOwnedLands(String playerName, LevelProfile profile) {
        if (profile == null) {
            return;
        }
        for (Land land : lands.getLands()) {
            if (land.isOwner(playerName)) {
                land.setLevelId(profile.getId());
            }
        }
    }

    public PlayerData getOrCreatePlayerData(Player player) {
        PlayerData data = getOrCreatePlayerData(player.getName());
        data.setLastName(player.getName());
        data.startSession(System.currentTimeMillis());
        return data;
    }

    public PlayerData getOrCreatePlayerData(String playerName) {
        String key = Text.key(playerName);
        PlayerData data = players.get(key);
        if (data == null) {
            data = new PlayerData(key, playerName, 0L);
            players.put(key, data);
        }
        data.setLastName(playerName);
        return data;
    }

    public PlayerData getPlayerData(String playerName) {
        return players.get(Text.key(playerName));
    }

    public void startSession(Player player) {
        getOrCreatePlayerData(player);
    }

    public void stopSession(Player player) {
        PlayerData data = players.get(Text.key(player.getName()));
        if (data != null) {
            data.stopSession(System.currentTimeMillis());
        }
    }

    public void settleAllSessions() {
        long now = System.currentTimeMillis();
        for (PlayerData data : players.values()) {
            if (data.isOnlineSession()) {
                data.settle(now);
            }
        }
    }
}
