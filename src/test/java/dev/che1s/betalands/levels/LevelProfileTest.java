package dev.che1s.betalands.levels;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class LevelProfileTest {
    @Test
    public void defaultLevelsMatchConfiguredQuotas() {
        List<LevelProfile> profiles = LevelService.createDefaultProfiles();
        LevelProfile defaultLevel = profiles.get(0);
        LevelProfile vip = profiles.get(1);
        LevelProfile svip = profiles.get(2);

        assertEquals(5, defaultLevel.getMaxMainLands());
        assertEquals(100, defaultLevel.getMaxMainChunks());
        assertEquals("Default", defaultLevel.getDisplayName());
        assertEquals(3, defaultLevel.getMaxSubclaimsPerMain());
        assertEquals(8, defaultLevel.getMaxFamilyMembers());
        assertEquals(5, defaultLevel.getMaxJoinedLands());
        assertEquals(15, vip.getMaxMainLands());
        assertEquals(200, vip.getMaxMainChunks());
        assertEquals(10, vip.getMaxSubclaimsPerMain());
        assertEquals(15, vip.getMaxFamilyMembers());
        assertEquals(30, vip.getMaxJoinedLands());
        assertEquals(30, svip.getMaxMainLands());
        assertEquals(300, svip.getMaxMainChunks());
        assertEquals(50, svip.getMaxSubclaimsPerMain());
        assertEquals(40, svip.getMaxFamilyMembers());
        assertEquals(100, svip.getMaxJoinedLands());
        assertEquals(20.0D, defaultLevel.getClaimChunkPrice(), 0.0D);
        assertEquals(20.0D, vip.getClaimChunkPrice(), 0.0D);
        assertEquals(20.0D, svip.getClaimChunkPrice(), 0.0D);
        assertEquals(5.0D, defaultLevel.getClaimChunkIncrement(), 0.0D);
        assertEquals(4.0D, vip.getClaimChunkIncrement(), 0.0D);
        assertEquals(3.0D, svip.getClaimChunkIncrement(), 0.0D);
        assertEquals(0.30D, defaultLevel.getMaintenancePerChunkPerDay(), 0.0D);
        assertEquals(0.50D, defaultLevel.getMaintenancePerMemberPerDay(), 0.0D);
        assertEquals(0.25D, vip.getMaintenancePerChunkPerDay(), 0.0D);
        assertEquals(0.40D, vip.getMaintenancePerMemberPerDay(), 0.0D);
        assertEquals(0.20D, svip.getMaintenancePerChunkPerDay(), 0.0D);
        assertEquals(0.30D, svip.getMaintenancePerMemberPerDay(), 0.0D);
        assertEquals(3, defaultLevel.getMaintenanceIntervalDays());
        assertEquals(3, vip.getMaintenanceIntervalDays());
        assertEquals(3, svip.getMaintenanceIntervalDays());
    }

    @Test
    public void maintenanceIntervalCannotBeLessThanOneDay() {
        LevelProfile profile = new LevelProfile("test", "Test", "", 0,
                1, 1, 1, 1, 1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0);

        assertEquals(1, profile.getMaintenanceIntervalDays());
    }
}
