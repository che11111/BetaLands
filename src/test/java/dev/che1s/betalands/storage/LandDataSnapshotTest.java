package dev.che1s.betalands.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.Rect2i;
import dev.che1s.betalands.service.LandManager;

public class LandDataSnapshotTest {
    @Test
    public void preservesMaintenanceExemptionInSnapshot() {
        LandManager manager = new LandManager(null);
        ArrayList<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land live = new Land("land-2", "Alice", "Beta", "world", null,
                areas, 0, 127, true, 100L, "manual", null);
        live.setMaintenanceExempt(true);
        live.setBankBalance(37.5D);
        manager.getOrCreateRentAccount("Charlie").setBalance(9.25D);
        manager.setServerMaintenanceBank(123.45D);
        manager.addLoadedLand(live);

        LandDataSnapshot snapshot = LandDataSnapshot.capture(manager, 1000L);
        assertTrue(snapshot.getLands().get(0).isMaintenanceExempt());
        assertEquals(37.5D, snapshot.getLands().get(0).getBankBalance(), 0.0D);
        assertEquals(9.25D,
                snapshot.getRentAccounts().get("charlie").getBalance(), 0.0D);
        assertEquals(123.45D, snapshot.getServerMaintenanceBank(), 0.0D);
    }

    @Test
    public void snapshotIsIsolatedFromLaterLiveChanges() {
        LandManager manager = new LandManager(null);
        ArrayList<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land live = new Land("land-1", "Alice", "Alpha", "world", null,
                areas, 0, 127, true, 100L, "manual", null);
        manager.addLoadedLand(live);
        PlayerData livePlayer = new PlayerData("alice", "Alice", 60L);
        manager.getPlayers().put("alice", livePlayer);
        manager.getOrCreateRentAccount("Charlie").setBalance(2.0D);

        LandDataSnapshot snapshot = LandDataSnapshot.capture(manager, 1000L);
        Land copied = snapshot.getLands().get(0);
        PlayerData copiedPlayer = snapshot.getPlayers().get("alice");

        live.rename("Renamed");
        live.setOwner("Bob");
        live.setBankBalance(99.0D);
        live.setMaintenanceExempt(true);
        live.setMemberPermissions("Charlie", EnumSet.of(LandPermission.BUILD));
        manager.getOrCreateRentAccount("Charlie").setBalance(12.0D);
        livePlayer.setLevelId("vip");

        assertEquals("Alpha", copied.getName());
        assertEquals("Alice", copied.getOwner());
        assertEquals(0.0D, copied.getBankBalance(), 0.0D);
        assertFalse(copied.isMaintenanceExempt());
        assertFalse(copied.getMembers().containsKey("charlie"));
        assertEquals(2.0D,
                snapshot.getRentAccounts().get("charlie").getBalance(), 0.0D);
        assertEquals("default", copiedPlayer.getLevelId());
    }
}
