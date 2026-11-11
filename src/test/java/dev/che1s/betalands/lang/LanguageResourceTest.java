package dev.che1s.betalands.lang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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
        assertNotNull(readResource("/lang/console-zh.yml"));
        assertNotNull(readResource("/lang/console-en.yml"));
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
