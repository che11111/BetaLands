package dev.che1s.betalands.lang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.util.config.Configuration;
import org.junit.Test;

public class LanguageResourceTest {
    @Test
    public void gameMessagesAreAsciiOnly() throws Exception {
        assertAscii("/lang/game.yml");
        assertTrue(readResource("/lang/game.yml").contains(
                "&b{land}&e. Owner: &a{owner}&e"));
    }

    @Test
    public void mapMessagesExistInBothLanguages() throws Exception {
        String english = readResource("/lang/map-en.yml");
        String chinese = readResource("/lang/map-zh.yml");
        assertNotNull(english);
        assertNotNull(chinese);
        assertTrue(english.contains("main-set:"));
        assertTrue(english.contains("subclaim-set:"));
        assertTrue(english.contains("marker-set:"));
        assertTrue(chinese.contains("main-set:"));
        assertTrue(chinese.contains("subclaim-set:"));
        assertTrue(chinese.contains("marker-set:"));
        assertTrue(english.contains("chunks:"));
        assertTrue(english.contains("id:"));
        assertTrue(english.contains("bank:"));
        assertTrue(english.contains("maintenance:"));
        assertTrue(english.contains("rent-enabled:"));
        assertTrue(english.contains("flags:"));
        assertTrue(english.contains("member-list-header-v3:"));
        assertTrue(english.contains("member-permission-inline-v3:"));
        assertTrue(english.contains("member-no-permissions-v3:"));
        assertTrue(english.contains("member-list-note-v3:"));
        assertTrue(english.contains("member-separator:"));
        assertTrue(english.contains("flag-state-v2:"));
        assertTrue(chinese.contains("chunks:"));
        assertTrue(chinese.contains("id:"));
        assertTrue(chinese.contains("bank:"));
        assertTrue(chinese.contains("maintenance:"));
        assertTrue(chinese.contains("rent-enabled:"));
        assertTrue(chinese.contains("flags:"));
        assertTrue(chinese.contains("member-list-header-v3:"));
        assertTrue(chinese.contains("member-permission-inline-v3:"));
        assertTrue(chinese.contains("member-no-permissions-v3:"));
        assertTrue(chinese.contains("member-list-note-v3:"));
        assertTrue(chinese.contains("member-separator:"));
        assertTrue(chinese.contains("flag-state-v2:"));
        assertAscii("/lang/map-en.yml");
    }

    private void assertAscii(String path) throws Exception {
        String text = readResource(path);
        for (int i = 0; i < text.length(); i++) {
            assertFalse("Non-ASCII character in " + path + " at index " + i,
                    text.charAt(i) > 127);
        }
    }

    @Test
    public void consoleLanguageFilesExist() throws Exception {
        String[] files = {
            "console-en.yml", "console-zh.yml", "console-ja.yml",
            "console-ko.yml", "console-zh-tw.yml", "console-de.yml",
            "console-fr.yml", "console-es.yml", "console-ru.yml"
        };
        Configuration english = loadConfiguration("/lang/console-en.yml");
        for (String file : files) {
            Configuration config = loadConfiguration("/lang/" + file);
            assertEquals("Key mismatch in " + file,
                new HashSet<String>(english.getAll().keySet()),
                new HashSet<String>(config.getAll().keySet()));
        }
    }

    @Test
    public void mapLanguageFilesExist() throws Exception {
        String[] files = {
            "map-en.yml", "map-zh.yml", "map-ja.yml",
            "map-ko.yml", "map-zh-tw.yml", "map-de.yml",
            "map-fr.yml", "map-es.yml", "map-ru.yml"
        };
        Set<String> englishKeys = mapKeys(readResource("/lang/map-en.yml"));
        for (String file : files) {
            assertEquals("Key mismatch in " + file,
                englishKeys, mapKeys(readResource("/lang/" + file)));
        }
    }

    private Set<String> mapKeys(String content) {
        Set<String> keys = new HashSet<String>();
        String section = null;
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() == 0 || trimmed.startsWith("#")
                    || trimmed.indexOf(':') < 0) {
                continue;
            }
            String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
            int indent = line.indexOf(trimmed);
            if (indent == 0) {
                section = key;
            } else if (indent == 2 && section != null) {
                keys.add(section + "." + key);
            }
        }
        return keys;
    }

    private Configuration loadConfiguration(String path) throws Exception {
        URL resource = getClass().getResource(path);
        assertNotNull("Missing resource " + path, resource);
        Configuration configuration = new Configuration(new File(resource.toURI()));
        configuration.load();
        return configuration;
    }

    private String readResource(String path) throws Exception {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull("Missing resource " + path, input);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        input.close();
        return new String(output.toByteArray(), "UTF-8");
    }
}
