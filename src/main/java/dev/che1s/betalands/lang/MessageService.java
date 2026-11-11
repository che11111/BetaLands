package dev.che1s.betalands.lang;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.config.Configuration;

import dev.che1s.betalands.BetaLandsPlugin;

public final class MessageService {
    private final BetaLandsPlugin plugin;
    private Configuration game;
    private Configuration console;
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
        File consoleZh = ensureFile(folder, "console-zh.yml");
        File consoleEn = ensureFile(folder, "console-en.yml");
        File mapZh = ensureFile(folder, "map-zh.yml");
        File mapEn = ensureFile(folder, "map-en.yml");
        String consoleLanguage = resolvedLanguage(plugin.getSettings().getConsoleLanguage());
        File consoleFile = isChinese(consoleLanguage) ? consoleZh : consoleEn;
        String selectedMapLanguage = resolvedLanguage(plugin.getSettings().getMapLanguage());
        mapChinese = isChinese(selectedMapLanguage);
        File mapFile = isChinese(selectedMapLanguage) ? mapZh : mapEn;

        game = new Configuration(gameFile);
        game.load();
        console = new Configuration(consoleFile);
        console.load();
        mapLanguage = new Configuration(mapFile);
        mapLanguage.load();
        gamePrefix = ChatColor.YELLOW + "[BetaLands] ";
        consolePrefix = console.getString("prefix", "[BetaLands]");
    }

    public String game(String key, String fallback, String... replacements) {
        String value = game == null ? fallback : game.getString(key, fallback);
        return gamePrefix + formatBody(value, replacements);
    }

    public String gameRaw(String key, String fallback, String... replacements) {
        String value = game == null ? fallback : game.getString(key, fallback);
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
        String value = console == null ? fallback : console.getString(key, fallback);
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

    private String resolvedLanguage(String configured) {
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
