package dev.che1s.betalands.integration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapServiceLifecycleTest {
    @Test
    public void disableResetsUnhookedLifecycleState() {
        MapService service = new MapService(null);
        assertEquals("not-hooked(not-checked)", service.dynmapDiagnostic());
        assertEquals("not-hooked(not-checked)", service.blueMapDiagnostic());

        service.disable();

        assertEquals("not-hooked(not-checked)", service.dynmapDiagnostic());
        assertEquals("not-hooked(not-checked)", service.blueMapDiagnostic());
    }
}
