package dev.che1s.betalands.levels;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.bukkit.util.config.Configuration;
import org.junit.Test;

import dev.che1s.betalands.ConfigFileMerger;

public class LevelsConfigMigrationTest {
    @Test
    public void addsMaintenanceIntervalToEveryExistingLevel() throws Exception {
        String defaults = ""
                + "default-level: default\n"
                + "levels:\n"
                + "  default:\n"
                + "    prices:\n"
                + "      maintenance-interval-days: 3\n"
                + "      maintenance-per-chunk-per-day: 0.30\n"
                + "  vip:\n"
                + "    prices:\n"
                + "      maintenance-interval-days: 3\n"
                + "      maintenance-per-chunk-per-day: 0.25\n"
                + "  svip:\n"
                + "    prices:\n"
                + "      maintenance-interval-days: 3\n"
                + "      maintenance-per-chunk-per-day: 0.20\n";
        String existing = ""
                + "default-level: vip\n"
                + "levels:\n"
                + "  default:\n"
                + "    prices:\n"
                + "      maintenance-per-chunk-per-day: 9.00\n"
                + "  vip:\n"
                + "    prices:\n"
                + "      maintenance-per-chunk-per-day: 8.00\n"
                + "  svip:\n"
                + "    prices:\n"
                + "      maintenance-per-chunk-per-day: 7.00\n";

        java.util.List<String> merged = ConfigFileMerger.mergeMissingKeys(
                Arrays.asList(defaults.split("\\n")),
                Arrays.asList(existing.split("\\n")));
        File file = File.createTempFile("betalands-levels-", ".yml");
        try {
            Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            Configuration configuration = new Configuration(file);
            configuration.load();
            assertEquals("vip", configuration.getString("default-level"));
            assertEquals(3, configuration.getInt(
                    "levels.default.prices.maintenance-interval-days", 0));
            assertEquals(3, configuration.getInt(
                    "levels.vip.prices.maintenance-interval-days", 0));
            assertEquals(3, configuration.getInt(
                    "levels.svip.prices.maintenance-interval-days", 0));
            assertEquals(8.0D, configuration.getDouble(
                    "levels.vip.prices.maintenance-per-chunk-per-day", 0.0D), 0.0D);
        } finally {
            file.delete();
        }
    }
}
