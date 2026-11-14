package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.economy.EconomyService;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.util.Text;

public final class LandMaintenanceService {
    public static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final BetaLandsPlugin plugin;

    public LandMaintenanceService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void runMaintenance() {
        long now = System.currentTimeMillis();
        boolean changed = runServerMaintenance(now);
        changed |= runRentCharges(now);
        if (changed) {
            plugin.requestSaveAsync(true);
        }
    }

    private boolean runServerMaintenance(long now) {
        boolean changed = false;
        for (Land land : plugin.getLandManager().getLands()) {
            if (land.isSubclaim()) {
                continue;
            }
            long last = land.getLastMaintenanceAt();
            if (last <= 0L) {
                land.setLastMaintenanceAt(now);
                changed = true;
                continue;
            }
            long elapsed = now - last;
            LevelProfile profile = plugin.getLevels().getProfile(land);
            int intervalDays = profile.getMaintenanceIntervalDays();
            double maintenanceFee = getMaintenanceFee(land);
            MaintenanceAccrual accrual = calculateMaintenanceAccrual(
                    last, now, intervalDays, maintenanceFee);
            if (!accrual.isDue()) {
                continue;
            }
            long days = accrual.getDays();
            long intervals = accrual.getIntervals();
            long next = accrual.getNextAt();
            if (land.isMaintenanceExempt()) {
                land.setLastMaintenanceAt(next);
                changed = true;
                continue;
            }
            if (maintenanceFee <= 0.0D) {
                land.setLastMaintenanceAt(next);
                changed = true;
                continue;
            }

            double due = accrual.getAmount();
            double collected = coveredAmount(due, land.getBankBalance());
            land.addBankBalance(-due);
            if (collected > 0.0D) {
                plugin.getLandManager().addServerMaintenanceBank(collected);
                plugin.getAuditLog().logEconomy("SERVER_MAINTENANCE_CHARGE",
                        "SYSTEM", land, collected,
                        plugin.getLandManager().getServerMaintenanceBank(),
                        "Land maintenance from land bank for " + intervals
                                + " interval(s), " + days
                                + " day(s), owner=" + land.getOwner(), true);
            }
            if (land.getBankBalance() < 0.0D) {
                plugin.getAuditLog().logEconomy("SERVER_MAINTENANCE_DEBT",
                        "SYSTEM", land, due, land.getBankBalance(),
                        "Land bank cannot cover the maintenance cycle, owner="
                                + land.getOwner(), true);
            }
            land.setLastMaintenanceAt(next);
            changed = true;
            notifyOwner(land, collected, intervals, days);
        }
        return changed;
    }

    private boolean runRentCharges(long now) {
        Map<String, List<RentCharge>> charges =
                new LinkedHashMap<String, List<RentCharge>>();
        boolean changed = false;
        for (Land land : plugin.getLandManager().getLands()) {
            if (!land.isRentEnabled()) {
                continue;
            }
            long last = land.getLastRentAt();
            long elapsed = now - last;
            if (elapsed < DAY_MILLIS) {
                continue;
            }
            long days = elapsed / DAY_MILLIS;
            double due = calculateRent(land.getRentAmount(), days);
            if (due > 0.0D) {
                for (String playerKey
                        : plugin.getLandManager().getRentPayers(land)) {
                    List<RentCharge> playerCharges = charges.get(playerKey);
                    if (playerCharges == null) {
                        playerCharges = new ArrayList<RentCharge>();
                        charges.put(playerKey, playerCharges);
                    }
                    playerCharges.add(new RentCharge(land, due, days));
                }
            }
            land.setLastRentAt(last + days * DAY_MILLIS);
            changed = true;
        }

        for (Map.Entry<String, List<RentCharge>> entry : charges.entrySet()) {
            RentAccount account = plugin.getLandManager()
                    .getOrCreateRentAccount(entry.getKey());
            if (account.getBalance() < 0.0D) {
                notifyRentDebt(account);
                continue;
            }

            List<RentCharge> playerCharges = entry.getValue();
            Collections.sort(playerCharges, new Comparator<RentCharge>() {
                @Override
                public int compare(RentCharge first, RentCharge second) {
                    return compareRentCharges(first.land, second.land);
                }
            });

            double processed = 0.0D;
            for (RentCharge charge : playerCharges) {
                double collected = coveredAmount(
                        charge.due, account.getBalance());
                account.addBalance(-charge.due);
                processed = EconomyService.round(
                        processed + charge.due).doubleValue();
                Land bankLand = getBankLand(charge.land);
                if (bankLand != null && collected > 0.0D) {
                    creditLandBank(bankLand, collected);
                }
                plugin.getAuditLog().logEconomy("RENT_CHARGE", "SYSTEM",
                        charge.land, collected, account.getBalance(),
                        "Global rent account, player=" + account.getPlayerName()
                                + ", due=" + plugin.getEconomy().format(charge.due)
                                + ", days=" + charge.days, true);
                if (account.getBalance() < 0.0D) {
                    account.setDebtLandId(charge.land.getId());
                    plugin.getAuditLog().logEconomy("RENT_DEBT", "SYSTEM",
                            charge.land, charge.due, account.getBalance(),
                            "Rent debt assigned to land, player="
                                    + account.getPlayerName(), true);
                    break;
                }
            }
            if (account.getBalance() >= 0.0D) {
                account.setDebtLandId(null);
            }
            notifyRentCharge(account, processed);
        }
        return changed;
    }

    /**
     * Amount charged for one complete maintenance interval.
     */
    public double getMaintenanceFee(Land land) {
        if (!plugin.getSettings().isEconomyEnabled()
                || (!plugin.getEconomy().isAvailable()
                && !plugin.getSettings().isEconomyRequired())) {
            return 0.0D;
        }
        Land main = getBankLand(land);
        if (main == null || main.isMaintenanceExempt()) {
            return 0.0D;
        }
        LevelProfile profile = plugin.getLevels().getProfile(main);
        int chunks = plugin.getLandManager().getOccupiedChunks(main);
        int members = plugin.getLandManager().getFamilyMemberCount(main);
        return calculateMaintenanceFee(profile, chunks, members);
    }

    static double calculateMaintenanceFee(LevelProfile profile, int chunks, int members) {
        return EconomyService.round(
                Math.max(0, chunks) * profile.getMaintenancePerChunkPerCycle()
                        + Math.max(0, members)
                        * profile.getMaintenancePerMemberPerCycle())
                .doubleValue();
    }

    static long completedMaintenanceIntervals(long elapsed, int intervalDays) {
        long intervalMillis = Math.max(1, intervalDays) * DAY_MILLIS;
        return elapsed < intervalMillis ? 0L : elapsed / intervalMillis;
    }

    static MaintenanceAccrual calculateMaintenanceAccrual(
            long last, long now, int intervalDays, double maintenanceFee) {
        int safeInterval = Math.max(1, intervalDays);
        long completed = completedMaintenanceIntervals(
                now - last, safeInterval);
        long days = completed * safeInterval;
        return new MaintenanceAccrual(completed > 0L, completed, days,
                last + days * DAY_MILLIS,
                EconomyService.round(
                        Math.max(0.0D, maintenanceFee) * completed)
                        .doubleValue());
    }

    public Land getBankLand(Land land) {
        if (land == null) {
            return null;
        }
        if (!land.isSubclaim()) {
            return land;
        }
        return plugin.getLandManager().getById(land.getParentId());
    }

    public void setMaintenanceExempt(Land land, boolean exempt) {
        Land main = getBankLand(land);
        if (main == null) {
            return;
        }
        main.setMaintenanceExempt(exempt);
        if (exempt && main.getBankBalance() < 0.0D) {
            main.setBankBalance(0.0D);
        }
    }

    public long getNextDueAt(Land land) {
        Land main = getBankLand(land);
        if (main == null) {
            return 0L;
        }
        LevelProfile profile = plugin.getLevels().getProfile(main);
        return main.getLastMaintenanceAt()
                + profile.getMaintenanceIntervalDays() * DAY_MILLIS;
    }

    public long getNextRentAt(Land rentedLand) {
        return rentedLand == null ? 0L : rentedLand.getLastRentAt() + DAY_MILLIS;
    }

    public boolean isRentInDebt(Land land, String playerName) {
        RentAccount account = plugin.getLandManager().getRentAccount(playerName);
        if (account == null || account.getBalance() >= 0.0D
                || account.getDebtLandId() == null) {
            return false;
        }
        Land debtLand = plugin.getLandManager().getById(account.getDebtLandId());
        if (debtLand == null || land == null) {
            return false;
        }
        return debtLand.getId().equals(land.getId())
                || (!debtLand.isSubclaim()
                && land.getParentId() != null
                && debtLand.getId().equals(land.getParentId()));
    }

    public boolean hasMaintenanceDebt(Land land) {
        Land main = getBankLand(land);
        return main != null && !main.isMaintenanceExempt()
                && main.getBankBalance() < 0.0D;
    }

    static double calculateRent(double dailyRent, long days) {
        return EconomyService.round(
                Math.max(0.0D, dailyRent) * Math.max(0L, days)).doubleValue();
    }

    static int compareRentCharges(Land first, Land second) {
        int amount = Double.compare(
                first.getRentAmount(), second.getRentAmount());
        return amount != 0 ? amount : first.getDisplayName()
                .compareToIgnoreCase(second.getDisplayName());
    }

    public static double coveredAmount(double due, double balance) {
        return EconomyService.round(Math.min(
                Math.max(0.0D, due), Math.max(0.0D, balance))).doubleValue();
    }

    public static double debtSettledByDeposit(double amount, double balance) {
        return EconomyService.round(Math.min(
                Math.max(0.0D, amount),
                Math.max(0.0D, -balance))).doubleValue();
    }

    public double creditLandBank(Land main, double amount) {
        if (main == null || amount <= 0.0D) {
            return 0.0D;
        }
        double debtPaid = debtSettledByDeposit(amount, main.getBankBalance());
        main.addBankBalance(amount);
        if (debtPaid > 0.0D) {
            plugin.getLandManager().addServerMaintenanceBank(debtPaid);
        }
        return debtPaid;
    }

    /**
     * @deprecated Maintenance is charged per interval, not per day.
     */
    @Deprecated
    public double getDailyFee(Land land) {
        return getMaintenanceFee(land);
    }

    private void notifyOwner(Land land, double amount, long intervals,
                             long days) {
        Player owner = plugin.getServer().getPlayerExact(land.getOwner());
        if (owner == null) {
            return;
        }
        if (amount > 0.0D) {
            plugin.getMessages().send(owner, "maintenance.server-charged-v2",
                    "&7Land maintenance charged from {land} bank: {amount} ({intervals} cycle(s), {days} days). Land bank={balance}.",
                    "land", land.getDisplayName(),
                    "amount", plugin.getEconomy().format(amount),
                    "intervals", Long.toString(intervals),
                    "days", Long.toString(days),
                    "balance", plugin.getEconomy().format(land.getBankBalance()));
        }
        if (land.getBankBalance() < 0.0D) {
            plugin.getMessages().send(owner, "maintenance.debt-v2",
                    "&cThe {land} land bank cannot cover the maintenance cycle. Balance={balance}. Deposit with /land bank deposit <amount> {land}.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(land.getBankBalance()));
        }
    }

    private void notifyRentCharge(RentAccount account, double processed) {
        Player player = plugin.getServer().getPlayer(account.getPlayerName());
        if (player == null) {
            return;
        }
        plugin.getMessages().send(player, "rent.charged-v2",
                "&7Rent charged this cycle: {amount}. Global rent account={balance}.",
                "amount", plugin.getEconomy().format(processed),
                "balance", plugin.getEconomy().format(account.getBalance()));
        if (account.getBalance() < 0.0D) {
            notifyRentDebt(account);
        }
    }

    private void notifyRentDebt(RentAccount account) {
        Player player = plugin.getServer().getPlayer(account.getPlayerName());
        if (player == null) {
            return;
        }
        Land debtLand = plugin.getLandManager().getById(account.getDebtLandId());
        plugin.getMessages().send(player, "rent.debt-notice-v3",
                "&cYour rent account is negative and the unpaid rent belongs to {land}. Deposit with /land rent deposit <amount>.",
                "land", debtLand == null ? "-" : debtLand.getDisplayName());
    }

    private static final class RentCharge {
        private final Land land;
        private final double due;
        private final long days;

        private RentCharge(Land land, double due, long days) {
            this.land = land;
            this.due = due;
            this.days = days;
        }
    }

    static final class MaintenanceAccrual {
        private final boolean due;
        private final long intervals;
        private final long days;
        private final long nextAt;
        private final double amount;

        private MaintenanceAccrual(boolean due, long intervals, long days,
                                   long nextAt, double amount) {
            this.due = due;
            this.intervals = intervals;
            this.days = days;
            this.nextAt = nextAt;
            this.amount = amount;
        }

        boolean isDue() {
            return due;
        }

        long getDays() {
            return days;
        }

        long getIntervals() {
            return intervals;
        }

        long getNextAt() {
            return nextAt;
        }

        double getAmount() {
            return amount;
        }
    }
}
