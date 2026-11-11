package dev.che1s.betalands.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.UUID;

import org.junit.Test;

public class LandIdentityTest {
    @Test
    public void usesImmutableUuidIds() {
        String first = LandIdentity.newId();
        String second = LandIdentity.newId();
        assertEquals(first, UUID.fromString(first).toString());
        assertEquals(second, UUID.fromString(second).toString());
        assertNotEquals(first, second);
    }

    @Test
    public void createsStableTownyIds() {
        String first = LandIdentity.stableTownyId("world", "Qing");
        String second = LandIdentity.stableTownyId("world", "qing");
        assertEquals(first, second);
        assertEquals(first, UUID.fromString(first).toString());
    }
}
