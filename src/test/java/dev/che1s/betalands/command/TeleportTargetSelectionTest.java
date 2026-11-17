package dev.che1s.betalands.command;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class TeleportTargetSelectionTest {
    @Test
    public void singleOwnedMainLandIsSelectedWithoutLookingAtPlayerPosition() {
        Land owned = land("alice:aaa", "Alice", "AAA", null);

        List<Land> candidates = TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(owned), lands(owned));

        assertEquals(1, candidates.size());
        assertEquals(owned, candidates.get(0));
    }

    @Test
    public void ownSubclaimsDoNotCountAsAdditionalLands() {
        Land main = land("alice:aaa", "Alice", "AAA", null);
        Land subclaim = land("alice:aaa/shop", "Alice", "shop", main.getId());

        List<Land> candidates = TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(main), lands(main, subclaim));

        assertEquals(1, candidates.size());
        assertEquals(main, candidates.get(0));
    }

    @Test
    public void singleJoinedLandIsSelected() {
        Land joined = land("bob:town", "Bob", "Town", null);

        List<Land> candidates = TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(), lands(joined));

        assertEquals(1, candidates.size());
        assertEquals(joined, candidates.get(0));
    }

    @Test
    public void multipleOwnedOrJoinedLandsRequireAnExplicitName() {
        Land first = land("alice:aaa", "Alice", "AAA", null);
        Land second = land("alice:bbb", "Alice", "BBB", null);
        Land joined = land("bob:town", "Bob", "Town", null);

        assertEquals(2, TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(first, second), lands()).size());
        assertEquals(2, TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(first), lands(first, joined)).size());
        assertEquals(2, TeleportCommandHandler.teleportLandCandidates(
                "Alice", lands(), lands(joined,
                        land("carol:city", "Carol", "City", null))).size());
    }

    private Land land(String id, String owner, String name, String parentId) {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land(id, owner, name, "world", parentId, areas,
                0, 127, true, 0L, "manual", null);
    }

    private List<Land> lands(Land... values) {
        List<Land> result = new ArrayList<Land>();
        for (Land value : values) {
            result.add(value);
        }
        return result;
    }
}
