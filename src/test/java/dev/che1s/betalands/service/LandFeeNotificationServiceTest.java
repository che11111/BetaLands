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
    public void ignoresLandsWithoutDailyFees() {
        assertEquals(LandFeeNotificationService.FeeState.FREE,
                LandFeeNotificationService.evaluate(-100.0D, 0.0D));
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
                LandFeeNotificationService.evaluate(main.getBankBalance(), 3.0D));
    }
}
