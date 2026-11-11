package dev.che1s.betalands.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.che1s.betalands.util.Text;

public final class LandIdentity {
    private LandIdentity() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String stableTownyId(String scope, String name) {
        return UUID.nameUUIDFromBytes(("betalands-towny:" + Text.key(scope) + ":"
                + Text.key(name)).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
