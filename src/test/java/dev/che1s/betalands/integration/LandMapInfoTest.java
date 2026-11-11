package dev.che1s.betalands.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.Rect2i;

public class LandMapInfoTest {
    @Test
    public void countsEachOccupiedChunkOnce() {
        assertEquals(1, LandMapInfo.occupiedChunks(Arrays.asList(
                new Rect2i(0, 0, 7, 7),
                new Rect2i(8, 8, 15, 15))));
        assertEquals(2, LandMapInfo.occupiedChunks(Arrays.asList(
                new Rect2i(0, 0, 15, 15),
                new Rect2i(16, 0, 31, 15))));
    }

    @Test
    public void countsNegativeChunksCorrectly() {
        assertEquals(4, LandMapInfo.occupiedChunks(Arrays.asList(
                new Rect2i(-32, -32, -1, -1))));
    }

    @Test
    public void groupsMembersWithIdenticalEffectivePermissions() {
        Map<String, EnumSet<LandPermission>> members =
                new LinkedHashMap<String, EnumSet<LandPermission>>();
        members.put("steve1", EnumSet.of(LandPermission.BREAK));
        members.put("steve2", EnumSet.of(LandPermission.BREAK));
        members.put("alice", EnumSet.of(LandPermission.BUILD, LandPermission.BREAK,
                LandPermission.USE, LandPermission.CONTAINER,
                LandPermission.RENT));
        members.put("bob", EnumSet.of(LandPermission.BUILD, LandPermission.BREAK,
                LandPermission.USE, LandPermission.CONTAINER,
                LandPermission.RENT));

        List<LandMapInfo.PermissionGroup> groups = LandMapInfo.groupPermissions(members);
        assertEquals(2, groups.size());
        LandMapInfo.PermissionGroup shared = LandMapInfo.chooseSharedGroup(
                groups, LandPermission.defaultsForMember(false));
        assertNotNull(shared);
        assertEquals(Arrays.asList("alice", "bob"), shared.getPlayers());
        assertEquals(LandPermission.defaultsForMember(false), shared.getPermissions());
    }

    @Test
    public void keepsSingletonPermissionGroupVisible() {
        Map<String, EnumSet<LandPermission>> members =
                new LinkedHashMap<String, EnumSet<LandPermission>>();
        members.put("alice", EnumSet.of(LandPermission.MANAGE));

        List<LandMapInfo.PermissionGroup> groups = LandMapInfo.groupPermissions(members);
        assertNull(LandMapInfo.chooseSharedGroup(
                groups, LandPermission.defaultsForMember(false)));
    }
}
