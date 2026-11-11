package dev.che1s.betalands.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerDataTest {
    @Test
    public void countsSessionTimeAcrossSaves() {
        PlayerData data = new PlayerData("alex", "Alex", 24L * 3600L - 119L);
        data.startSession(1000L);
        assertEquals(24L * 3600L - 60L, data.getTotalSeconds(60000L));
        data.settle(60000L);
        data.stopSession(120000L);
        assertEquals(24L * 3600L, data.getTotalSeconds(999999L));
    }

    @Test
    public void onlyActiveSessionsAdvance() {
        PlayerData data = new PlayerData("alex", "Alex", 5L);
        assertFalse(data.isOnlineSession());
        assertEquals(5L, data.getTotalSeconds(999999L));
        data.startSession(1000L);
        assertTrue(data.isOnlineSession());
        data.stopSession(3000L);
        assertEquals(7L, data.getTotalSeconds(999999L));
    }

    @Test
    public void tracksManagedLevelExpiration() {
        PlayerData data = new PlayerData("steve", "Steve", 0L);
        data.setManagedLevel("vip", 5000L);
        assertTrue(data.isLevelManaged());
        assertEquals("vip", data.getLevelId());
        assertTrue(data.isManagedLevelExpired(5000L));
        assertFalse(data.isManagedLevelExpired(4999L));
        data.setManagedLevel("default", 0L);
        assertFalse(data.isManagedLevelExpired(Long.MAX_VALUE));
    }
}
