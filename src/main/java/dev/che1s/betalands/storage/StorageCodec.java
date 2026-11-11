package dev.che1s.betalands.storage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.Rect2i;

/**
 * Shared scalar encoding used by TSV and SQL persistence.
 */
final class StorageCodec {
    private StorageCodec() {
    }

    static String areas(List<Rect2i> values) {
        StringBuilder result = new StringBuilder();
        for (Rect2i area : values) {
            if (result.length() > 0) {
                result.append('|');
            }
            result.append(area.serialize());
        }
        return result.toString();
    }

    static List<Rect2i> parseAreas(String value) {
        List<Rect2i> result = new ArrayList<Rect2i>();
        if (value == null) {
            return result;
        }
        for (String token : value.split("\\|")) {
            if (token.length() > 0) {
                result.add(Rect2i.parse(token));
            }
        }
        return result;
    }

    static String permissions(EnumSet<LandPermission> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringBuilder result = new StringBuilder();
        for (LandPermission permission : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(permission.getId());
        }
        return result.toString();
    }

    static EnumSet<LandPermission> parsePermissions(String value) {
        EnumSet<LandPermission> result =
                EnumSet.noneOf(LandPermission.class);
        if (value == null || value.length() == 0 || "-".equals(value)) {
            return result;
        }
        for (String token : value.split(",")) {
            LandPermission permission = LandPermission.parse(token);
            if (permission != null) {
                result.add(permission);
            }
        }
        return result;
    }

    static Map<LandFlag, Boolean> parseFlags(String value) {
        Map<LandFlag, Boolean> result =
                new EnumMap<LandFlag, Boolean>(LandFlag.class);
        if (value == null || value.length() == 0 || "-".equals(value)) {
            return result;
        }
        for (String token : value.split(";")) {
            int separator = token.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            LandFlag flag = LandFlag.parse(token.substring(0, separator));
            String state = token.substring(separator + 1);
            if (flag != null) {
                result.put(flag, "allow".equalsIgnoreCase(state)
                        || "true".equalsIgnoreCase(state));
            }
        }
        return result;
    }

    static String serializeFlags(Land land) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<LandFlag, Boolean> entry
                : land.getFlagOverrides().entrySet()) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(entry.getKey().getId()).append('=')
                    .append(entry.getValue().booleanValue() ? "allow" : "deny");
        }
        return result.length() == 0 ? "-" : result.toString();
    }

}
