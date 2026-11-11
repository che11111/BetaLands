package dev.che1s.betalands.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyAmountParserTest {
    @Test
    public void acceptsAmountsUpToConfiguredPrecision() {
        MoneyAmountParser.Result result =
                MoneyAmountParser.parse("1.99", 2);
        assertEquals(MoneyAmountParser.Status.SUCCESS, result.getStatus());
        assertEquals(1.99D, result.getAmount(), 0.0D);
    }

    @Test
    public void rejectsLongDecimalsBeforeCreatingOversizedValues() {
        MoneyAmountParser.Result result =
                MoneyAmountParser.parse("1.9999999999999", 2);
        assertEquals(MoneyAmountParser.Status.TOO_MANY_DECIMALS,
                result.getStatus());
    }

    @Test
    public void rejectsUnsupportedNumericForms() {
        assertEquals(MoneyAmountParser.Status.INVALID,
                MoneyAmountParser.parse("1e6", 2).getStatus());
        assertEquals(MoneyAmountParser.Status.INVALID,
                MoneyAmountParser.parse("-1", 2).getStatus());
        assertEquals(MoneyAmountParser.Status.INVALID,
                MoneyAmountParser.parse("123456789012345678901234567890123", 2)
                        .getStatus());
    }
}
