package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class LandInvitationServiceTest {
    @Test
    public void storesOnlyWhenPlayerAndLandMatchAndClearsWithLand() {
        LandInvitationService service = new LandInvitationService();
        Land first = land("land-one", "AAA");
        Land second = land("land-two", "BBB");

        service.create("Alice", "Builder", first);

        assertNotNull(service.get("Builder", first));
        assertNull(service.get("Builder", second));
        service.removeForLand(first.getId());
        assertNull(service.get("Builder", first));
    }

    private Land land(String id, String name) {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land(id, "Alice", name, "world", null,
                areas, 0, 127, true, 0L, "manual", null);
    }
}
