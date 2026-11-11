package dev.che1s.betalands.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.util.Text;

/**
 * Owns plugin-internal finance ledgers. iConomy transfers remain in EconomyService.
 */
public final class FinanceService {
    private final Map<String, RentAccount> rentAccounts =
            new LinkedHashMap<String, RentAccount>();
    private double serverMaintenanceBank;

    public void clear() {
        rentAccounts.clear();
        serverMaintenanceBank = 0.0D;
    }

    public double getServerMaintenanceBank() {
        return serverMaintenanceBank;
    }

    public void setServerMaintenanceBank(double balance) {
        serverMaintenanceBank = Math.max(0.0D, balance);
    }

    public void addServerMaintenanceBank(double amount) {
        serverMaintenanceBank = Math.max(0.0D, serverMaintenanceBank + amount);
    }

    public Map<String, RentAccount> getRentAccounts() {
        return rentAccounts;
    }

    public RentAccount getRentAccount(String playerName) {
        return rentAccounts.get(Text.key(playerName));
    }

    public RentAccount getOrCreateRentAccount(String playerName) {
        String key = Text.key(playerName);
        RentAccount account = rentAccounts.get(key);
        if (account == null) {
            account = new RentAccount(playerName, 0.0D);
            rentAccounts.put(key, account);
        }
        return account;
    }

    public static Map<String, Double> distributeProportionally(
            double total, Map<String, Double> weights) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        if (weights == null || weights.isEmpty()) {
            return result;
        }
        double totalWeight = 0.0D;
        for (Double weight : weights.values()) {
            if (weight != null && weight.doubleValue() > 0.0D) {
                totalWeight += weight.doubleValue();
            }
        }
        if (totalWeightsAreEmpty(totalWeight)) {
            return result;
        }
        long totalCents = BigDecimal.valueOf(Math.max(0.0D, total))
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValue();
        List<Allocation> allocations = new ArrayList<Allocation>();
        long assigned = 0L;
        int order = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            double weight = entry.getValue() == null
                    ? 0.0D : Math.max(0.0D, entry.getValue().doubleValue());
            BigDecimal exact = BigDecimal.valueOf(totalCents)
                    .multiply(BigDecimal.valueOf(weight))
                    .divide(BigDecimal.valueOf(totalWeight), 12, RoundingMode.DOWN)
                    .setScale(0, RoundingMode.DOWN);
            long cents = exact.longValue();
            assigned += cents;
            allocations.add(new Allocation(entry.getKey(), cents,
                    BigDecimal.valueOf(totalCents)
                            .multiply(BigDecimal.valueOf(weight))
                            .divide(BigDecimal.valueOf(totalWeight), 12,
                                    RoundingMode.HALF_UP)
                    .subtract(BigDecimal.valueOf(cents)), order++));
        }
        Collections.sort(allocations, new Comparator<Allocation>() {
            @Override
            public int compare(Allocation first, Allocation second) {
                return second.remainder.compareTo(first.remainder);
            }
        });
        long remainder = totalCents - assigned;
        for (int index = 0; index < remainder; index++) {
            allocations.get(index % allocations.size()).cents++;
        }
        Collections.sort(allocations, new Comparator<Allocation>() {
            @Override
            public int compare(Allocation first, Allocation second) {
                return first.order - second.order;
            }
        });
        for (Allocation allocation : allocations) {
            result.put(allocation.key,
                    BigDecimal.valueOf(allocation.cents)
                            .movePointLeft(2).doubleValue());
        }
        return result;
    }

    private static boolean totalWeightsAreEmpty(double totalWeight) {
        return totalWeight <= 0.0D;
    }

    private static final class Allocation {
        private final String key;
        private long cents;
        private final BigDecimal remainder;
        private final int order;

        private Allocation(String key, long cents, BigDecimal remainder, int order) {
            this.key = key;
            this.cents = cents;
            this.remainder = remainder;
            this.order = order;
        }
    }
}
