package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class LandMaintenanceServiceTest {
    @Test
    public void calculatesMaintenanceAndRent() {
        LevelProfile profile = new LevelProfile("default", "默认", "", 0,
                5, 100, 3, 8, 5, 300.0D, 20.0D, 5.0D, 0.01D, 0.30D, 0.50D);
        assertEquals(18.0D,
                LandMaintenanceService.calculateMaintenanceFee(profile, 50, 6), 0.0001D);
        assertEquals(9.0D, LandMaintenanceService.calculateRent(3.0D, 3L), 0.0001D);

        LevelProfile vip = new LevelProfile("vip", "VIP", "", 10,
                15, 200, 10, 15, 30, 250.0D, 20.0D, 4.0D, 0.008D, 0.25D, 0.40D);
        LevelProfile svip = new LevelProfile("svip", "SVIP", "", 20,
                30, 300, 50, 40, 100, 200.0D, 20.0D, 3.0D, 0.005D, 0.20D, 0.30D);
        assertEquals(14.90D,
                LandMaintenanceService.calculateMaintenanceFee(vip, 50, 6), 0.0001D);
        assertEquals(11.80D,
                LandMaintenanceService.calculateMaintenanceFee(svip, 50, 6), 0.0001D);
    }

    @Test
    public void enablingExemptionClearsExistingDebt() {
        Land main = new Land("main", "Alice", "Town", "world", null,
                Collections.singletonList(new Rect2i(0, 0, 15, 15)),
                0, 127, true, 1L, "manual", null);
        main.setBankBalance(-12.5D);
        LandMaintenanceService service = new LandMaintenanceService(null);

        service.setMaintenanceExempt(main, true);

        assertEquals(true, main.isMaintenanceExempt());
        assertEquals(0.0D, main.getBankBalance(), 0.0D);
    }

    @Test
    public void onlyPrepaidMoneyIsCollected() {
        assertEquals(5.0D,
                LandMaintenanceService.coveredAmount(10.0D, 5.0D), 0.0D);
        assertEquals(0.0D,
                LandMaintenanceService.coveredAmount(10.0D, -5.0D), 0.0D);
        assertEquals(10.0D,
                LandMaintenanceService.coveredAmount(10.0D, 25.0D), 0.0D);
    }

    @Test
    public void maintenanceRunsOnlyAfterCompleteIntervals() {
        long day = LandMaintenanceService.DAY_MILLIS;
        assertEquals(0L,
                LandMaintenanceService.completedMaintenanceIntervals(2L * day, 3));
        assertEquals(1L,
                LandMaintenanceService.completedMaintenanceIntervals(3L * day, 3));
        assertEquals(2L,
                LandMaintenanceService.completedMaintenanceIntervals(7L * day, 3));
    }

    @Test
    public void maintenanceAccrualChargesWholeCyclesOnly() {
        long day = LandMaintenanceService.DAY_MILLIS;
        long last = 10_000L;

        LandMaintenanceService.MaintenanceAccrual early =
                LandMaintenanceService.calculateMaintenanceAccrual(
                        last, last + 2L * day, 3, 4.0D);
        assertFalse(early.isDue());

        LandMaintenanceService.MaintenanceAccrual due =
                LandMaintenanceService.calculateMaintenanceAccrual(
                        last, last + 3L * day + day / 2L, 3, 4.0D);
        assertTrue(due.isDue());
        assertEquals(3L, due.getDays());
        assertEquals(12.0D, due.getAmount(), 0.0D);
        assertEquals(last + 3L * day, due.getNextAt());

        LandMaintenanceService.MaintenanceAccrual catchUp =
                LandMaintenanceService.calculateMaintenanceAccrual(
                        last, last + 10L * day, 3, 4.0D);
        assertEquals(9L, catchUp.getDays());
        assertEquals(36.0D, catchUp.getAmount(), 0.0D);
        assertEquals(last + 9L * day, catchUp.getNextAt());
    }

    @Test
    public void depositsSettleDebtBeforeBecomingPrepaidBalance() {
        assertEquals(15.0D,
                LandMaintenanceService.debtSettledByDeposit(20.0D, -15.0D), 0.0D);
        assertEquals(5.0D,
                LandMaintenanceService.debtSettledByDeposit(5.0D, -15.0D), 0.0D);
        assertEquals(0.0D,
                LandMaintenanceService.debtSettledByDeposit(20.0D, 3.0D), 0.0D);
    }

    @Test
    public void cheaperRentIsChargedFirst() {
        Land cheap = new Land("cheap", "Alice", "Cheap", "world", null,
                Collections.singletonList(new Rect2i(0, 0, 15, 15)),
                0, 127, true, 1L, "manual", null);
        cheap.setRentAmount(3.0D);
        Land expensive = new Land("expensive", "Alice", "Expensive", "world", null,
                Collections.singletonList(new Rect2i(32, 0, 47, 15)),
                0, 127, true, 2L, "manual", null);
        expensive.setRentAmount(10.0D);

        assertTrue(LandMaintenanceService.compareRentCharges(
                cheap, expensive) < 0);
    }
}
