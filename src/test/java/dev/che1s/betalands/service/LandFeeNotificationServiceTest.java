package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.Rect2i;

public class LandFeeNotificationServiceTest {
    @Test
    public void detectsDebtAndSevenDayLowBalanceThreshold() {
        assertEquals(LandFeeNotificationService.FeeState.DEBT,
                LandFeeNotificationService.evaluate(-0.01D, 10.0D));
        assertEquals(LandFeeNotificationService.FeeState.LOW,
                LandFeeNotificationService.evaluate(69.99D, 10.0D));
        assertEquals(LandFeeNotificationService.FeeState.OK,
                LandFeeNotificationService.evaluate(70.0D, 10.0D));
    }

    @Test
    public void ignoresLandsWithoutMaintenanceFees() {
        assertEquals(LandFeeNotificationService.FeeState.FREE,
                LandFeeNotificationService.evaluateMaintenance(
                        -100.0D, 0.0D));
        assertEquals("-", LandFeeNotificationService.coveredDays(100.0D, 0.0D));
    }

    @Test
    public void formatsRemainingCoverage() {
        assertEquals("6.9", LandFeeNotificationService.coveredDays(69.0D, 10.0D));
        assertEquals("0.0", LandFeeNotificationService.coveredDays(-10.0D, 10.0D));
    }

    @Test
    public void landBankBalanceIsTheMaintenancePrepayment() {
        Land main = new Land("main", "Alice", "Home", "world", null,
                Collections.singletonList(new Rect2i(0, 0, 15, 15)),
                0, 127, true, 1L, "manual", null);
        main.setBankBalance(23.50D);
        assertEquals(23.50D, main.getBankBalance(), 0.0D);
        assertEquals(LandFeeNotificationService.FeeState.OK,
                LandFeeNotificationService.evaluateMaintenance(
                        main.getBankBalance(), 3.0D));
    }

    @Test
    public void maintenanceWarnsWhenBalanceCannotCoverNextCycle() {
        assertEquals(LandFeeNotificationService.FeeState.LOW,
                LandFeeNotificationService.evaluateMaintenance(
                        2.99D, 3.0D));
        assertEquals(LandFeeNotificationService.FeeState.OK,
                LandFeeNotificationService.evaluateMaintenance(
                        3.0D, 3.0D));
        assertEquals("2.0", LandFeeNotificationService
                .coveredMaintenanceCycles(6.0D, 3.0D));
    }
}
