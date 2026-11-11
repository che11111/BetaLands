package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class FinanceServiceTest {
    @Test
    public void distributesEveryCentByMaintenanceWeight() {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put("A", 10.0D);
        weights.put("B", 35.0D);
        weights.put("C", 65.0D);

        Map<String, Double> shares =
                FinanceService.distributeProportionally(1000.0D, weights);

        assertEquals(90.91D, shares.get("A"), 0.0D);
        assertEquals(318.18D, shares.get("B"), 0.0D);
        assertEquals(590.91D, shares.get("C"), 0.0D);
        assertEquals(1000.0D,
                shares.get("A") + shares.get("B") + shares.get("C"), 0.0D);
    }

    @Test
    public void excludesZeroMaintenanceLands() {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put("paid", 4.0D);
        weights.put("exempt", 0.0D);

        Map<String, Double> shares =
                FinanceService.distributeProportionally(8.0D, weights);

        assertEquals(8.0D, shares.get("paid"), 0.0D);
        assertEquals(0.0D, shares.get("exempt"), 0.0D);
    }
}
