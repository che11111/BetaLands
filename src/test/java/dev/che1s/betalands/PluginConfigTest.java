package dev.che1s.betalands;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.bukkit.util.config.Configuration;
import org.junit.Test;

public class PluginConfigTest {
    @Test
    public void addsMissingKeysWithoutOverwritingExistingValues() throws Exception {
        List<String> defaults = Arrays.asList(
                "language:",
                "  console: auto",
                "  map: auto",
                "storage:",
                "  type: sqlite",
                "  sqlite:",
                "    file: betalands.db",
                "  mysql:",
                "    host: 127.0.0.1",
                "maps:",
                "  colors:",
                "    dynmap:",
                "      main: auto",
                "      subclaim: auto",
                "    bluemap:",
                "      main: auto",
                "      subclaim: auto",
                "  refresh-delay-seconds: 15",
                "economy:",
                "  admin-created-maintenance-exempt: true",
                "logs:",
                "  retention-days: 10",
                "protection:",
                "  op-bypass: true");
        List<String> current = Arrays.asList(
                "language:",
                "  console: en",
                "storage:",
                "  type: mysql");

        List<String> merged = PluginConfig.mergeMissingKeys(defaults, current);
        File file = File.createTempFile("betalands-config-", ".yml");
        try {
            Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            Configuration configuration = new Configuration(file);
            configuration.load();
            assertEquals("en", configuration.getString("language.console"));
            assertEquals("auto", configuration.getString("language.map"));
            assertEquals("mysql", configuration.getString("storage.type"));
            assertEquals("betalands.db", configuration.getString("storage.sqlite.file"));
            assertEquals("127.0.0.1", configuration.getString("storage.mysql.host"));
            assertEquals("auto", configuration.getString("maps.colors.dynmap.main"));
            assertEquals("auto", configuration.getString("maps.colors.bluemap.subclaim"));
            assertEquals(15, configuration.getInt("maps.refresh-delay-seconds", 0));
            assertEquals(true, configuration.getBoolean(
                    "economy.admin-created-maintenance-exempt", false));
            assertEquals(10, configuration.getInt(
                    "logs.retention-days", 0));
            assertEquals(true, configuration.getBoolean("protection.op-bypass", false));
        } finally {
            file.delete();
        }
    }

    @Test
    public void addsNestedMapColorsToExistingMapSection() throws Exception {
        List<String> defaults = Arrays.asList(
                "maps:",
                "  show-subclaims: true",
                "  dynmap:",
                "    enabled: true",
                "  bluemap:",
                "    enabled: true",
                "  colors:",
                "    dynmap:",
                "      main: auto",
                "      subclaim: auto",
                "    bluemap:",
                "      main: auto",
                "      subclaim: auto");
        List<String> current = Arrays.asList(
                "maps:",
                "  show-subclaims: false");

        List<String> merged = PluginConfig.mergeMissingKeys(defaults, current);
        File file = File.createTempFile("betalands-config-", ".yml");
        try {
            Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            Configuration configuration = new Configuration(file);
            configuration.load();
            assertEquals(true, configuration.getBoolean("maps.dynmap.enabled", false));
            assertEquals(true, configuration.getBoolean("maps.bluemap.enabled", false));
            assertEquals(false, configuration.getBoolean("maps.show-subclaims", true));
            assertEquals("auto", configuration.getString("maps.colors.dynmap.main"));
            assertEquals("auto", configuration.getString("maps.colors.bluemap.main"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void splitsDynmapAndBlueMapRefreshConfiguration() throws Exception {
        List<String> defaults = Arrays.asList(
                "maps:",
                "  dynmap:",
                "    daily-refresh-hour: 1",
                "    daily-refresh-minute: 0",
                "  bluemap:",
                "    refresh-delay-seconds: 15");
        List<String> current = Arrays.asList(
                "maps:",
                "  enabled: true");

        List<String> merged = PluginConfig.mergeMissingKeys(defaults, current);
        File file = File.createTempFile("betalands-map-refresh-", ".yml");
        try {
            Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            Configuration configuration = new Configuration(file);
            configuration.load();
            assertEquals(1, configuration.getInt(
                    "maps.dynmap.daily-refresh-hour", 0));
            assertEquals(0, configuration.getInt(
                    "maps.dynmap.daily-refresh-minute", -1));
            assertEquals(15, configuration.getInt(
                    "maps.bluemap.refresh-delay-seconds", 0));
        } finally {
            file.delete();
        }
    }

    @Test
    public void addsMoneyDecimalPrecisionSetting() throws Exception {
        List<String> defaults = Arrays.asList(
                "economy:",
                "  enabled: true",
                "  max-decimal-places: 2");
        List<String> current = Arrays.asList(
                "economy:",
                "  enabled: true");

        List<String> merged = PluginConfig.mergeMissingKeys(defaults, current);
        File file = File.createTempFile("betalands-money-precision-", ".yml");
        try {
            Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            Configuration configuration = new Configuration(file);
            configuration.load();
            assertEquals(2, configuration.getInt(
                    "economy.max-decimal-places", 0));
        } finally {
            file.delete();
        }
    }
}
