package dev.che1s.betalands.util;

import java.util.Locale;

public final class Text {
    private Text() {
    }

    public static String color(String text) {
        return text == null ? "" : text.replace('&', '\u00a7');
    }

    public static String key(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    public static String slug(String text) {
        String lower = key(text);
        StringBuilder result = new StringBuilder(lower.length());
        boolean previousDash = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (valid) {
                result.append(c);
                previousDash = c == '-';
            } else if (!previousDash) {
                result.append('-');
                previousDash = true;
            }
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '-') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.length() == 0 ? "land" : result.toString();
    }

    public static String slugPreservingCase(String text) {
        String value = text == null ? "" : text.trim();
        StringBuilder result = new StringBuilder(value.length());
        boolean previousDash = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean valid = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-';
            if (character == '-') {
                if (!previousDash) {
                    result.append(character);
                }
                previousDash = true;
            } else if (valid) {
                result.append(character);
                previousDash = false;
            } else if (!previousDash) {
                result.append('-');
                previousDash = true;
            }
        }
        while (result.length() > 0 && result.charAt(0) == '-') {
            result.deleteCharAt(0);
        }
        while (result.length() > 0 && result.charAt(result.length() - 1) == '-') {
            result.deleteCharAt(result.length() - 1);
        }
        return result.length() == 0 ? "land" : result.toString();
    }

    public static boolean validName(String name, int minLength, int maxLength) {
        if (name == null) {
            return false;
        }
        int length = name.length();
        if (length < minLength || length > maxLength) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String unescape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped && c == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                if (c == 't') {
                    result.append('\t');
                } else if (c == 'n') {
                    result.append('\n');
                } else if (c == 'r') {
                    result.append('\r');
                } else {
                    result.append(c);
                }
                escaped = false;
            } else {
                result.append(c);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    public static String duration(long seconds) {
        long safe = Math.max(0L, seconds);
        long days = safe / 86400L;
        long hours = (safe % 86400L) / 3600L;
        long minutes = (safe % 3600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }
}
