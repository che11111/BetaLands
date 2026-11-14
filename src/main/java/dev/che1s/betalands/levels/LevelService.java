package dev.che1s.betalands.levels;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.ConfigFileMerger;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.PlayerData;

public final class LevelService {
    private final BetaLandsPlugin plugin;
    private final File file;
    private final Map<String, LevelProfile> profiles = new LinkedHashMap<String, LevelProfile>();
    private final List<LevelProfile> rankedProfiles = new ArrayList<LevelProfile>();
    private String defaultLevelId = "default";

    public LevelService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "levels.yml");
    }

    public void load() {
        profiles.clear();
        rankedProfiles.clear();
        ensureFile();
        ConfigFileMerger.ensureMissingKeys(plugin.getClass(), file,
                "/levels.yml", plugin.getServer().getLogger());
        Configuration configuration = new Configuration(file);
        configuration.load();
        defaultLevelId = configuration.getString("default-level", "default");
        List<String> levelIds = configuration.getKeys("levels");
        if (levelIds != null) {
            for (String levelId : levelIds) {
                ConfigurationNode node = configuration.getNode("levels." + levelId);
                if (node == null) {
                    continue;
                }
                ConfigurationNode prices = node.getNode("prices");
                if (prices == null) {
                    prices = Configuration.getEmptyNode();
                }
                String displayName = node.getString("display-name", levelId);
                if ("default".equalsIgnoreCase(levelId) && "默认".equals(displayName)) {
                    displayName = "Default";
                }
                LevelProfile profile = new LevelProfile(
                        levelId,
                        displayName,
                        node.getString("permission", "betalands.level." + levelId),
                        node.getInt("priority", 0),
                        node.getInt("max-main-lands", defaultMainLands(levelId)),
                        node.getInt("max-main-chunks", defaultMainChunks(levelId)),
                        node.getInt("max-subclaims-per-main",
                                defaultSubclaimsPerMain(levelId)),
                        node.getInt("max-family-members", defaultFamilyMembers(levelId)),
                        node.getInt("max-joined-lands", defaultJoinedLimit(levelId)),
                        prices.getDouble("create-main", 300.0D),
                        prices.getDouble("claim-chunk", 20.0D),
                        prices.getDouble("claim-chunk-increment",
                                defaultClaimIncrement(levelId)),
                        prices.getDouble("subclaim-per-block", 0.01D),
                        maintenanceRate(prices, true,
                                defaultMaintenancePerChunk(levelId)),
                        maintenanceRate(prices, false,
                                defaultMaintenancePerMember(levelId)),
                        prices.getInt("maintenance-interval-days", 3)
                );
                profiles.put(levelId.toLowerCase(java.util.Locale.ROOT), profile);
            }
        }
        if (profiles.isEmpty()) {
            for (LevelProfile profile : createDefaultProfiles()) {
                profiles.put(profile.getId(), profile);
            }
            defaultLevelId = "default";
        }
        rankedProfiles.addAll(profiles.values());
        Collections.sort(rankedProfiles, new Comparator<LevelProfile>() {
            @Override
            public int compare(LevelProfile first, LevelProfile second) {
                if (first.getPriority() != second.getPriority()) {
                    return first.getPriority() > second.getPriority() ? -1 : 1;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        if (!profiles.containsKey(defaultLevelId.toLowerCase(java.util.Locale.ROOT))) {
            defaultLevelId = profiles.keySet().iterator().next();
        }
    }

    public LevelProfile getProfile(Player player) {
        PlayerData data = plugin.getLandManager() == null ? null
                : plugin.getLandManager().getPlayerData(player.getName());
        if (data != null && data.isLevelManaged()) {
            return managedProfile(data, System.currentTimeMillis());
        }
        for (LevelProfile profile : rankedProfiles) {
            if (profile.getPermission() != null && profile.getPermission().length() > 0
                    && player.hasPermission(profile.getPermission())) {
                return profile;
            }
        }
        return getDefaultProfile();
    }

    public LevelProfile getProfile(String levelId) {
        LevelProfile profile = getProfileExact(levelId);
        return profile == null ? getDefaultProfile() : profile;
    }

    public LevelProfile getProfileExact(String levelId) {
        return levelId == null ? null
                : profiles.get(levelId.toLowerCase(java.util.Locale.ROOT));
    }

    public LevelProfile getProfile(Land land) {
        return land == null ? getDefaultProfile() : getProfile(land.getLevelId());
    }

    public LevelProfile getProfileForName(String playerName) {
        PlayerData data = plugin.getLandManager() == null ? null
                : plugin.getLandManager().getPlayerData(playerName);
        if (data != null && data.isLevelManaged()) {
            return managedProfile(data, System.currentTimeMillis());
        }
        Player online = plugin.getServer().getPlayer(playerName);
        if (online != null) {
            return getProfile(online);
        }
        return data == null ? getDefaultProfile() : getProfile(data.getLevelId());
    }

    public LevelProfile grantManagedLevel(String playerName, String levelId, long expiresAt) {
        LevelProfile profile = getProfileExact(levelId);
        if (profile == null || plugin.getLandManager() == null) {
            return null;
        }
        PlayerData data = plugin.getLandManager().getOrCreatePlayerData(playerName);
        data.setManagedLevel(profile.getId(), expiresAt);
        plugin.getLandManager().applyLevelToOwnedLands(playerName, profile);
        return profile;
    }

    public int refreshExpiredGrants() {
        if (plugin.getLandManager() == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int changed = 0;
        for (PlayerData data : new ArrayList<PlayerData>(
                plugin.getLandManager().getPlayers().values())) {
            if (data.isManagedLevelExpired(now)) {
                LevelProfile defaultProfile = getDefaultProfile();
                data.setManagedLevel(defaultProfile.getId(), 0L);
                plugin.getLandManager().applyLevelToOwnedLands(
                        data.getLastName(), defaultProfile);
                changed++;
            }
        }
        return changed;
    }

    private LevelProfile managedProfile(PlayerData data, long now) {
        if (data.isManagedLevelExpired(now)) {
            LevelProfile defaultProfile = getDefaultProfile();
            data.setManagedLevel(defaultProfile.getId(), 0L);
            if (plugin.getLandManager() != null) {
                plugin.getLandManager().applyLevelToOwnedLands(
                        data.getLastName(), defaultProfile);
            }
            return defaultProfile;
        }
        return getProfile(data.getLevelId());
    }

    public LevelProfile getDefaultProfile() {
        LevelProfile profile = profiles.get(defaultLevelId.toLowerCase(java.util.Locale.ROOT));
        return profile == null ? createDefaultProfiles().get(0) : profile;
    }

    public List<LevelProfile> getProfiles() {
        return Collections.unmodifiableList(rankedProfiles);
    }

    private void ensureFile() {
        if (file.isFile()) {
            return;
        }
        File folder = file.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            plugin.getMessages().warning("config.directory-create-failed",
                    "Could not create plugin data directory: {path}",
                    "path", folder.getAbsolutePath());
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = plugin.getClass().getResourceAsStream("/levels.yml");
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
            plugin.getMessages().warning("config.levels-create-failed",
                    "Could not create levels.yml: {error}",
                    "error", exception.getMessage());
        } finally {
            close(input);
            close(output);
        }
    }

    private int defaultJoinedLimit(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 30;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 100;
        }
        return 6;
    }

    private int defaultMainLands(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 15;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 30;
        }
        return 5;
    }

    private int defaultSubclaimsPerMain(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 20;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 100;
        }
        return 3;
    }

    private int defaultFamilyMembers(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 20;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 60;
        }
        return 6;
    }

    private double defaultMaintenancePerChunk(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 0.15D;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 0.10D;
        }
        return 0.20D;
    }

    private double defaultMaintenancePerMember(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 0.25D;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 0.20D;
        }
        return 0.30D;
    }

    private double defaultClaimIncrement(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 4.0D;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 3.0D;
        }
        return 5.0D;
    }

    private int defaultMainChunks(String levelId) {
        if ("vip".equalsIgnoreCase(levelId)) {
            return 400;
        }
        if ("svip".equalsIgnoreCase(levelId)) {
            return 1000;
        }
        return 200;
    }

    static double maintenanceRate(ConfigurationNode prices, boolean perChunk,
                                  double fallback) {
        String cycleKey = perChunk
                ? "maintenance-per-chunk-per-cycle"
                : "maintenance-per-member-per-cycle";
        String legacyKey = perChunk
                ? "maintenance-per-chunk-per-day"
                : "maintenance-per-member-per-day";
        if (prices.getProperty(legacyKey) != null) {
            return prices.getDouble(legacyKey, fallback);
        }
        return prices.getDouble(cycleKey, fallback);
    }

    private void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    static List<LevelProfile> createDefaultProfiles() {
        List<LevelProfile> profiles = new ArrayList<LevelProfile>();
        profiles.add(new LevelProfile("default", "Default", "betalands.level.default", 0,
                5, 200, 3, 6, 6, 300.0D, 20.0D, 5.0D, 0.01D, 0.20D, 0.30D, 3));
        profiles.add(new LevelProfile("vip", "VIP", "betalands.level.vip", 10,
                15, 400, 20, 20, 30, 250.0D, 20.0D, 4.0D, 0.008D, 0.15D, 0.25D, 3));
        profiles.add(new LevelProfile("svip", "SVIP", "betalands.level.svip", 20,
                30, 1000, 100, 60, 100, 200.0D, 20.0D, 3.0D, 0.005D, 0.10D, 0.20D, 3));
        return profiles;
    }
}
