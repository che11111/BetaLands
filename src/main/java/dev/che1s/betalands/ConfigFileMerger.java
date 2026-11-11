package dev.che1s.betalands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Adds newly shipped configuration keys without overwriting user values.
 */
public final class ConfigFileMerger {
    private ConfigFileMerger() {
    }

    public static void ensureMissingKeys(Class<?> resourceOwner, File file,
                                         String resource, Logger logger) {
        try {
            List<String> defaults = readResource(resourceOwner, resource);
            List<String> current = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> merged = mergeMissingKeys(defaults, current);
            if (!merged.equals(current)) {
                Files.write(file.toPath(), merged, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            logger.warning("[BetaLands] Could not add missing keys to "
                    + file.getName() + ": " + exception.getMessage());
        }
    }

    public static List<String> mergeMissingKeys(List<String> defaults,
                                                List<String> currentLines) {
        List<String> current = new ArrayList<String>(currentLines);
        Map<String, ConfigKey> existing = indexKeys(current);
        List<ConfigKey> required = parseKeys(defaults);
        for (ConfigKey key : required) {
            if (existing.containsKey(key.path)) {
                continue;
            }
            int insertion;
            String line;
            if (key.parentPath == null) {
                insertion = current.size();
                line = key.line.substring(key.indent);
            } else {
                ConfigKey parent = existing.get(key.parentPath);
                if (parent == null) {
                    continue;
                }
                insertion = sectionEnd(current, parent.lineIndex, parent.indent);
                int relative = Math.max(0, key.indent - key.parentIndent);
                StringBuilder indent = new StringBuilder();
                for (int i = 0; i < parent.indent + relative; i++) {
                    indent.append(' ');
                }
                line = indent + key.line.substring(key.indent);
            }
            current.add(insertion, line);
            existing = indexKeys(current);
        }
        return current;
    }

    private static List<String> readResource(Class<?> owner, String resource)
            throws IOException {
        InputStream input = owner.getResourceAsStream(resource);
        if (input == null) {
            return new ArrayList<String>();
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        try {
            List<String> lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } finally {
            reader.close();
        }
    }

    private static Map<String, ConfigKey> indexKeys(List<String> lines) {
        Map<String, ConfigKey> result = new LinkedHashMap<String, ConfigKey>();
        List<ConfigKey> stack = new ArrayList<ConfigKey>();
        for (int index = 0; index < lines.size(); index++) {
            ConfigKey key = parseKey(lines.get(index), index, stack);
            if (key != null) {
                result.put(key.path, key);
                while (!stack.isEmpty()
                        && stack.get(stack.size() - 1).indent >= key.indent) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(key);
            }
        }
        return result;
    }

    private static List<ConfigKey> parseKeys(List<String> lines) {
        List<ConfigKey> result = new ArrayList<ConfigKey>();
        List<ConfigKey> stack = new ArrayList<ConfigKey>();
        for (int index = 0; index < lines.size(); index++) {
            ConfigKey key = parseKey(lines.get(index), index, stack);
            if (key != null) {
                result.add(key);
                while (!stack.isEmpty()
                        && stack.get(stack.size() - 1).indent >= key.indent) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(key);
            }
        }
        return result;
    }

    private static ConfigKey parseKey(String line, int lineIndex, List<ConfigKey> stack) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        if (indent >= line.length() || line.charAt(indent) == '#') {
            return null;
        }
        String trimmed = line.substring(indent);
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String name = trimmed.substring(0, colon).trim();
        if (name.length() == 0 || name.indexOf(' ') >= 0) {
            return null;
        }
        ConfigKey parent = null;
        for (int index = stack.size() - 1; index >= 0; index--) {
            if (stack.get(index).indent < indent) {
                parent = stack.get(index);
                break;
            }
        }
        return new ConfigKey(parent == null ? name : parent.path + "." + name,
                parent == null ? null : parent.path, line, indent,
                parent == null ? 0 : parent.indent, lineIndex);
    }

    private static int sectionEnd(List<String> lines, int headerIndex, int headerIndent) {
        for (int index = headerIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.trim().length() == 0 || line.trim().startsWith("#")) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            if (indent <= headerIndent) {
                return index;
            }
        }
        return lines.size();
    }

    private static final class ConfigKey {
        private final String path;
        private final String parentPath;
        private final String line;
        private final int indent;
        private final int parentIndent;
        private final int lineIndex;

        private ConfigKey(String path, String parentPath, String line,
                          int indent, int parentIndent, int lineIndex) {
            this.path = path;
            this.parentPath = parentPath;
            this.line = line;
            this.indent = indent;
            this.parentIndent = parentIndent;
            this.lineIndex = lineIndex;
        }
    }
}
