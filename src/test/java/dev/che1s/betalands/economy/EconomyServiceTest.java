package dev.che1s.betalands.economy;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class EconomyServiceTest {
    @Test
    public void formatsDefaultLandPricesWithTwoDecimals() {
        EconomyService economy = new EconomyService(null);
        assertEquals("300.00", economy.format(300.0D));
        assertEquals("20.00", economy.format(20.0D));
        assertEquals("0.01", economy.format(0.01D));
        assertEquals("-15.50", economy.format(-15.5D));
    }

    @Test
    public void calculatesSubclaimVolumePriceToTwoDecimals() {
        assertEquals(new BigDecimal("5.01"),
                EconomyService.round(501L * 0.01D));
    }
}
