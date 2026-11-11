package dev.che1s.betalands.integration;

import java.util.Locale;

final class MapColor {
    static final int AUTO = -1;

    private MapColor() {
    }

    static int parse(String value) {
        if (value == null) {
            return AUTO;
        }
        String normalized = value.trim();
        if (normalized.length() == 0 || "auto".equalsIgnoreCase(normalized)) {
            return AUTO;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.toLowerCase(Locale.ROOT).startsWith("0x")) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            return AUTO;
        }
        return Integer.parseInt(normalized, 16);
    }

    static int red(int rgb) {
        return (rgb >> 16) & 0xff;
    }

    static int green(int rgb) {
        return (rgb >> 8) & 0xff;
    }

    static int blue(int rgb) {
        return rgb & 0xff;
    }
}
