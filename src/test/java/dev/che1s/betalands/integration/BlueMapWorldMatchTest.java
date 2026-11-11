package dev.che1s.betalands.integration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BlueMapWorldMatchTest {
    @Test
    public void matchesOlderBlueMapVersionsUsingWorldName() {
        assertTrue(BlueMapIntegration.matchesWorld("world", "world", null));
    }

    @Test
    public void matchesWorldThroughBlueMapUniqueId() {
        assertTrue(BlueMapIntegration.matchesWorld(
                "world", "0d7e043b-a1d9-4ed2-9e2d-0ac5c5c860e6",
                "0d7e043b-a1d9-4ed2-9e2d-0ac5c5c860e6"));
    }

    @Test
    public void rejectsUnrelatedBlueMapWorld() {
        assertFalse(BlueMapIntegration.matchesWorld(
                "skylands", "0d7e043b-a1d9-4ed2-9e2d-0ac5c5c860e6",
                "9d6ba78e-0383-4dbc-a352-24aa8c7e9297"));
    }
}
