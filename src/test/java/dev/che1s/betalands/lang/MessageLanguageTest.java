package dev.che1s.betalands.lang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bukkit.ChatColor;
import org.junit.Test;

public class MessageLanguageTest {
    @Test
    public void detectsChineseAndEnglishLanguageCodes() {
        assertTrue(MessageService.isChinese("zh"));
        assertTrue(MessageService.isChinese("zh_CN"));
        assertTrue(MessageService.isChinese("chinese"));
        assertFalse(MessageService.isChinese("en"));
        assertFalse(MessageService.isChinese("en_US"));
    }

    @Test
    public void resolvesCustomLanguageFilenamesSafely() {
        assertEquals("console-fr.yml",
                MessageService.resolveConsoleFile("console-fr.yml"));
        assertEquals("console-en.yml",
                MessageService.resolveConsoleFile("fr"));
        assertEquals("console-en.yml",
                MessageService.resolveConsoleFile("zh_CN"));
        assertEquals("console-en.yml",
                MessageService.resolveConsoleFile("../../console-fr.yml"));
        assertEquals("game.yml",
                MessageService.resolveGameFile("game-de.yml"));
        assertEquals("game.yml", MessageService.resolveGameFile("game.yml"));
        assertEquals("console-zh.yml",
                MessageService.consoleFileForLocale("zh", "CN"));
        assertEquals("console-zh-tw.yml",
                MessageService.consoleFileForLocale("zh", "TW"));
        assertEquals("console-ja.yml",
                MessageService.consoleFileForLocale("ja", "JP"));
        assertEquals("console-ko.yml",
                MessageService.consoleFileForLocale("ko", "KR"));
        assertEquals("console-de.yml",
                MessageService.consoleFileForLocale("de", "DE"));
        assertEquals("console-fr.yml",
                MessageService.consoleFileForLocale("fr", "FR"));
        assertEquals("console-es.yml",
                MessageService.consoleFileForLocale("es", "ES"));
        assertEquals("console-ru.yml",
                MessageService.consoleFileForLocale("ru", "RU"));
        assertEquals("map-zh.yml",
                MessageService.mapFileForLocale("zh", "CN"));
        assertEquals("map-zh-tw.yml",
                MessageService.mapFileForLocale("zh", "TW"));
        assertEquals("map-ja.yml",
                MessageService.mapFileForLocale("ja", "JP"));
        assertEquals("map-ko.yml",
                MessageService.mapFileForLocale("ko", "KR"));
        assertEquals("map-de.yml",
                MessageService.mapFileForLocale("de", "DE"));
        assertEquals("map-fr.yml",
                MessageService.mapFileForLocale("fr", "FR"));
        assertEquals("map-es.yml",
                MessageService.mapFileForLocale("es", "ES"));
        assertEquals("map-ru.yml",
                MessageService.mapFileForLocale("ru", "RU"));
        assertEquals("map-en.yml",
                MessageService.mapFileForLocale("pt", "BR"));
    }

    @Test
    public void resolvesMapLanguageFilenamesSafely() {
        assertEquals("map-fr.yml",
                MessageService.resolveMapFile("map-fr.yml"));
        assertEquals("console-fr.yml",
                MessageService.resolveMapFile("console-fr.yml"));
        assertEquals("my-custom-map.yml",
                MessageService.resolveMapFile("my-custom-map.yml"));
        assertEquals("map-en.yml", MessageService.resolveMapFile("zh"));
        assertEquals("map-en.yml", MessageService.resolveMapFile("en"));
        assertEquals("map-en.yml", MessageService.resolveMapFile("fr"));
        assertEquals("map-en.yml",
                MessageService.resolveMapFile("../../map-fr.yml"));
    }

    @Test
    public void formatsWhiteBodyWithTypedAccentColors() {
        assertEquals(ChatColor.WHITE.toString() + ChatColor.YELLOW + "Text "
                        + ChatColor.AQUA + "Alice" + ChatColor.YELLOW
                        + " rent "
                        + ChatColor.GREEN + "3.00" + ChatColor.YELLOW,
                MessageService.formatBody("&eText {player} rent {rent}",
                        "player", "Alice", "rent", "3.00"));
        assertEquals(ChatColor.WHITE.toString() + ChatColor.RED + "Failed: "
                        + ChatColor.RED + "bank debt" + ChatColor.RED
                        + " for "
                        + ChatColor.AQUA + "AAA" + ChatColor.RED,
                MessageService.formatBody("&cFailed: {reason} for {land}",
                        "reason", "bank debt", "land", "AAA"));
    }

    @Test
    public void preservesColorCodesAndRestoresTemplateColorAfterPlaceholder() {
        assertEquals(ChatColor.WHITE.toString() + ChatColor.RED + "Danger "
                        + ChatColor.AQUA + "Alice" + ChatColor.RED
                        + " again",
                MessageService.formatBody("&cDanger {player} again",
                        "player", "Alice"));
    }
}
