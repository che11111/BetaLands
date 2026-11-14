package dev.che1s.betalands.lang;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.config.Configuration;

import dev.che1s.betalands.BetaLandsPlugin;

public final class MessageService {
    private static final String[] CONSOLE_LANGUAGE_FILES = {
            "console-en.yml",
            "console-zh.yml",
            "console-ja.yml",
            "console-ko.yml",
            "console-zh-tw.yml",
            "console-de.yml",
            "console-fr.yml",
            "console-es.yml",
            "console-ru.yml"
    };
    private static final Pattern SAFE_LANGUAGE_FILE =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*\\.yml$");

    private final BetaLandsPlugin plugin;
    private Configuration game;
    private Configuration gameFallback;
    private Configuration console;
    private Configuration consoleFallback;
    private Configuration mapLanguage;
    private String gamePrefix;
    private String consolePrefix;
    private boolean mapChinese;

    public MessageService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getServer().getLogger().warning(
                    "[BetaLands] Could not create lang directory: " + folder.getAbsolutePath());
        }
        File gameFile = ensureFile(folder, "game.yml");
        File consoleEn = ensureFile(folder, "console-en.yml");
        for (String filename : CONSOLE_LANGUAGE_FILES) {
            ensureFile(folder, filename);
        }
        File mapZh = ensureFile(folder, "map-zh.yml");
        File mapEn = ensureFile(folder, "map-en.yml");
        String consoleFilename = resolveConsoleFile(
                plugin.getSettings().getConsoleLanguage());
        String gameFilename = resolveGameFile(
                plugin.getSettings().getGameLanguage());
        File requestedConsole = new File(folder, consoleFilename);
        File requestedGame = new File(folder, gameFilename);
        String selectedMapLanguage = resolvedLanguage(plugin.getSettings().getMapLanguage());
        mapChinese = isChinese(selectedMapLanguage);
        File mapFile = isChinese(selectedMapLanguage) ? mapZh : mapEn;

        consoleFallback = new Configuration(consoleEn);
        consoleFallback.load();
        console = requestedConsole.isFile()
                ? load(requestedConsole) : consoleFallback;
        gameFallback = new Configuration(gameFile);
        gameFallback.load();
        game = requestedGame.isFile() ? load(requestedGame) : gameFallback;
        mapLanguage = new Configuration(mapFile);
        mapLanguage.load();
        gamePrefix = fallbackValue(game, gameFallback,
                "prefix", ChatColor.YELLOW + "[BetaLands] ");
        consolePrefix = fallbackValue(console, consoleFallback,
                "prefix", "[BetaLands]");

        if (isInvalidLanguageValue(plugin.getSettings().getConsoleLanguage(),
                consoleFilename)) {
            warning("language.invalid-file",
                    "Invalid language filename '{file}'; using {fallback}.",
                    "file", plugin.getSettings().getConsoleLanguage(),
                    "fallback", "console-en.yml");
        } else if (!requestedConsole.isFile()) {
            warning("language.file-fallback",
                    "Language file {file} was not found; using {fallback}.",
                    "file", consoleFilename,
                    "fallback", "console-en.yml");
        }
        if (isInvalidLanguageValue(plugin.getSettings().getGameLanguage(),
                gameFilename)) {
            warning("language.invalid-file",
                    "Invalid language filename '{file}'; using {fallback}.",
                    "file", plugin.getSettings().getGameLanguage(),
                    "fallback", "game.yml");
        } else if (!requestedGame.isFile()) {
            warning("language.file-fallback",
                    "Language file {file} was not found; using {fallback}.",
                    "file", gameFilename,
                    "fallback", "game.yml");
        }
    }

    public String game(String key, String fallback, String... replacements) {
        String value = fallbackValue(game, gameFallback, key, fallback);
        return gamePrefix + formatBody(value, replacements);
    }

    public String gameRaw(String key, String fallback, String... replacements) {
        String value = fallbackValue(game, gameFallback, key, fallback);
        return formatBody(value, replacements);
    }

    public void send(CommandSender sender, String key, String fallback, String... replacements) {
        sender.sendMessage(game(key, fallback, replacements));
    }

    public String map(String key, String fallback, String... replacements) {
        String value = mapLanguage == null ? fallback : mapLanguage.getString(key, fallback);
        return replace(value, replacements);
    }

    public boolean isMapChinese() {
        return mapChinese;
    }

    public String console(String key, String fallback, String... replacements) {
        String value = fallbackValue(console, consoleFallback, key, fallback);
        return consolePrefix + " " + replace(value, replacements);
    }

    public void info(String key, String fallback, String... replacements) {
        plugin.getServer().getLogger().info(console(key, fallback, replacements));
    }

    public void warning(String key, String fallback, String... replacements) {
        plugin.getServer().getLogger().warning(console(key, fallback, replacements));
    }

    public void severe(String key, String fallback, String... replacements) {
        plugin.getServer().getLogger().severe(console(key, fallback, replacements));
    }

    private String replace(String value, String... replacements) {
        String result = value == null ? "" : value;
        if (replacements != null) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                result = result.replace("{" + replacements[i] + "}",
                        replacements[i + 1] == null ? "" : replacements[i + 1]);
            }
        }
        return result;
    }

    static String formatBody(String value, String... replacements) {
        Map<String, String> values = new HashMap<String, String>();
        if (replacements != null) {
            for (int index = 0; index + 1 < replacements.length; index += 2) {
                values.put(replacements[index], replacements[index + 1]);
            }
        }
        String template = value == null ? "" : value;
        StringBuilder result = new StringBuilder();
        result.append(ChatColor.WHITE);
        String active = ChatColor.WHITE.toString();
        for (int index = 0; index < template.length(); index++) {
            char current = template.charAt(index);
            if ((current == '&' || current == '\u00a7')
                    && index + 1 < template.length()) {
                char code = Character.toLowerCase(template.charAt(index + 1));
                if ("0123456789abcdefklmnor".indexOf(code) >= 0) {
                    String color = "\u00a7" + code;
                    result.append(color);
                    active = updatedActiveColor(active, code);
                    index++;
                    continue;
                }
            }
            if (current == '{') {
                int closing = template.indexOf('}', index + 1);
                if (closing > index) {
                    String token = template.substring(index + 1, closing);
                    if (values.containsKey(token)) {
                        result.append(styledValue(token, values.get(token)));
                        result.append(active);
                        index = closing;
                        continue;
                    }
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private static String updatedActiveColor(String active, char code) {
        if (code == 'r') {
            return ChatColor.WHITE.toString();
        }
        if ((code >= '0' && code <= '9')
                || (code >= 'a' && code <= 'f')) {
            return "\u00a7" + code;
        }
        return active.indexOf("\u00a7" + code) >= 0
                ? active : active + "\u00a7" + code;
    }

    private static String styledValue(String token, String value) {
        String text = value == null ? "" : value;
        String key = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if ("reason".equals(key) || "error".equals(key)) {
            return ChatColor.RED + text;
        }
        if ("state".equals(key)) {
            if ("allow".equalsIgnoreCase(text) || "enabled".equalsIgnoreCase(text)) {
                return ChatColor.GREEN + text;
            }
            if ("deny".equalsIgnoreCase(text) || "disabled".equalsIgnoreCase(text)) {
                return ChatColor.RED + text;
            }
            return ChatColor.YELLOW + text;
        }
        if ("accept".equals(key) || "deny".equals(key)) {
            return ChatColor.GREEN + text;
        }
        if (isNameToken(key)) {
            return ChatColor.AQUA + text;
        }
        if (isValueToken(key)) {
            return ChatColor.GREEN + text;
        }
        if (isTimeToken(key)) {
            return ChatColor.YELLOW + text;
        }
        return text;
    }

    private static boolean isNameToken(String key) {
        return "player".equals(key) || "owner".equals(key)
                || "inviter".equals(key) || "land".equals(key)
                || "debtland".equals(key) || "world".equals(key)
                || "source".equals(key) || "flag".equals(key)
                || "permission".equals(key) || "level".equals(key)
                || "id".equals(key) || "members".equals(key)
                || "lands".equals(key) || "pos".equals(key)
                || "file".equals(key) || "status".equals(key);
    }

    private static boolean isValueToken(String key) {
        return "amount".equals(key) || "balance".equals(key)
                || "cost".equals(key) || "price".equals(key)
                || "base".equals(key) || "increment".equals(key)
                || "required".equals(key) || "daily".equals(key)
                || "totaldaily".equals(key) || "totalbalance".equals(key)
                || "rent".equals(key) || "create".equals(key)
                || "claim".equals(key) || "subblock".equals(key)
                || "mainupkeep".equals(key) || "subupkeep".equals(key)
                || "chunkrate".equals(key) || "memberrate".equals(key)
                || "bank".equals(key) || "count".equals(key)
                || "chunks".equals(key) || "subclaims".equals(key)
                || "area".equals(key) || "miny".equals(key)
                || "maxy".equals(key) || "days".equals(key)
                || "limit".equals(key) || "hours".equals(key)
                || "places".equals(key)
                || "levels".equals(key) || "towns".equals(key)
                || "blocks".equals(key) || "added".equals(key)
                || "updated".equals(key) || "conflicts".equals(key)
                || "plots".equals(key) || "x".equals(key)
                || "y".equals(key) || "z".equals(key);
    }

    private static boolean isTimeToken(String key) {
        return "time".equals(key) || "expires".equals(key)
                || "duration".equals(key) || "remaining".equals(key);
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value)
                .replace("&r", "");
    }

    static String resolvedLanguage(String configured) {
        if (configured == null || "auto".equalsIgnoreCase(configured)) {
            String systemLanguage = System.getProperty("user.language",
                    Locale.getDefault().getLanguage());
            return isChinese(systemLanguage) ? "zh" : "en";
        }
        return isChinese(configured) ? "zh" : "en";
    }

    static boolean isChinese(String language) {
        String value = language == null ? "zh" : language.toLowerCase(Locale.ROOT);
        return value.startsWith("zh") || value.contains("chinese") || value.contains("中文");
    }

    static String consoleFileForLocale(String language, String country) {
        String value = (language == null ? "" : language)
                + "_" + (country == null ? "" : country);
        value = value.toLowerCase(Locale.ROOT);
        if (value.startsWith("zh")) {
            return value.contains("hant")
                    || value.contains("_tw")
                    || value.contains("_hk")
                    || value.contains("_mo")
                    || value.contains("traditional")
                    || value.contains("繁體")
                    ? "console-zh-tw.yml" : "console-zh.yml";
        }
        if (value.startsWith("ja") || value.startsWith("jp")
                || value.contains("japanese") || value.contains("日本語")) {
            return "console-ja.yml";
        }
        if (value.startsWith("ko") || value.startsWith("kr")
                || value.contains("korean") || value.contains("한국어")) {
            return "console-ko.yml";
        }
        if (value.startsWith("de") || value.contains("german")
                || value.contains("deutsch")) {
            return "console-de.yml";
        }
        if (value.startsWith("fr") || value.contains("french")
                || value.contains("français")) {
            return "console-fr.yml";
        }
        if (value.startsWith("es") || value.contains("spanish")
                || value.contains("español")) {
            return "console-es.yml";
        }
        if (value.startsWith("ru") || value.contains("russian")
                || value.contains("русский")) {
            return "console-ru.yml";
        }
        return "console-en.yml";
    }

    static String resolveConsoleFile(String configured) {
        String requested = configured == null ? "" : configured.trim();
        if (requested.isEmpty() || "auto".equalsIgnoreCase(requested)) {
            return consoleFileForLocale(
                    System.getProperty("user.language",
                            Locale.getDefault().getLanguage()),
                    System.getProperty("user.country",
                            Locale.getDefault().getCountry())
            );
        }
        return SAFE_LANGUAGE_FILE.matcher(requested).matches()
                ? requested : "console-en.yml";
    }

    static String resolveGameFile(String configured) {
        String requested = configured == null ? "" : configured.trim();
        if (requested.isEmpty()) {
            return "game.yml";
        }
        return "game.yml".equals(requested) ? "game.yml" : "game.yml";
    }

    private static boolean isInvalidLanguageValue(String configured,
                                                   String resolved) {
        String requested = configured == null ? "" : configured.trim();
        if (requested.isEmpty() || "auto".equalsIgnoreCase(requested)) {
            return false;
        }
        return !requested.equals(resolved);
    }

    private Configuration load(File file) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        return configuration;
    }

    private String fallbackValue(Configuration primary, Configuration fallback,
                                 String key, String defaultValue) {
        String value = primary == null ? null : primary.getString(key, null);
        if (value == null && fallback != null && fallback != primary) {
            value = fallback.getString(key, null);
        }
        return value == null ? defaultValue : value;
    }

    private File ensureFile(File folder, String name) {
        File target = new File(folder, name);
        if (target.isFile()) {
            return target;
        }
        InputStream input = null;
        OutputStream output = null;
        try {
            input = plugin.getClass().getResourceAsStream("/lang/" + name);
            if (input != null) {
                output = new FileOutputStream(target);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (IOException exception) {
            plugin.getServer().getLogger().warning(
                    "[BetaLands] Could not create lang/" + name + ": " + exception.getMessage());
        } finally {
            close(input);
            close(output);
        }
        return target;
    }

    private void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
