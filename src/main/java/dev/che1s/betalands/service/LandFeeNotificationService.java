package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.util.Text;

public final class LandFeeNotificationService {
    static final int RENT_WARNING_DAYS = 7;

    private final BetaLandsPlugin plugin;
    private final LandManager lands;

    public LandFeeNotificationService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.lands = plugin.getLandManager();
    }

    public void scheduleJoinReminder(final Player player) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    notifyOnJoin(player);
                }
            }
        }, 20L);
    }

    public void notifyOnJoin(Player player) {
        List<String> lines = new ArrayList<String>();
        List<Land> rentedLands = new ArrayList<Land>();
        String playerKey = Text.key(player.getName());
        for (Land land : lands.getLands()) {
            if (!land.isSubclaim() && land.isOwner(player.getName())) {
                appendMaintenanceStatus(lines, land);
            }
            if (land.isRentEnabled()
                    && lands.getRentPayers(land).contains(playerKey)) {
                rentedLands.add(land);
            }
        }
        appendRentStatus(lines, player, rentedLands);
        if (lines.isEmpty()) {
            return;
        }
        plugin.getMessages().send(player, "fees.header", "&6[Land fees]");
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    private void appendMaintenanceStatus(List<String> lines, Land land) {
        if (land.isMaintenanceExempt()) {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.maintenance-exempt",
                    "&7{land}: land maintenance is exempt.",
                    "land", land.getDisplayName()));
            return;
        }
        double maintenance = plugin.getMaintenanceService().getMaintenanceFee(land);
        int intervalDays = plugin.getLevels().getProfile(land)
                .getMaintenanceIntervalDays();
        double balance = land.getBankBalance();
        FeeState state = evaluateMaintenance(balance, maintenance);
        if (state == FeeState.FREE) {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.maintenance-free",
                    "&7{land}: no maintenance charge.",
                    "land", land.getDisplayName()));
        } else if (state == FeeState.DEBT) {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.maintenance-debt-v3",
                    "&c{land}: land bank is in debt at {balance}. Maintenance={maintenance}/{days} days. Deposit with /land bank deposit <amount> {land}.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance),
                    "maintenance", plugin.getEconomy().format(maintenance),
                    "days", Integer.toString(intervalDays)));
        } else if (state == FeeState.LOW) {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.maintenance-low-v3",
                    "&e{land}: land bank={balance}, next charge={maintenance}/{days} days; covered cycles={cycles}. Deposit with /land bank deposit <amount> {land}.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance),
                    "maintenance", plugin.getEconomy().format(maintenance),
                    "days", Integer.toString(intervalDays),
                    "cycles", coveredMaintenanceCycles(
                            balance, maintenance)));
        } else {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.maintenance-status-v3",
                    "&7{land}: land bank={balance}, maintenance={maintenance}/{days} days, covers {cycles} cycles.",
                    "land", land.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance),
                    "maintenance", plugin.getEconomy().format(maintenance),
                    "days", Integer.toString(intervalDays),
                    "cycles", coveredMaintenanceCycles(
                            balance, maintenance)));
        }
    }

    private void appendRentStatus(List<String> lines, Player player,
                                  List<Land> rentedLands) {
        if (rentedLands.isEmpty()) {
            return;
        }
        double totalDaily = totalDailyRent(rentedLands);
        RentAccount account = lands.getRentAccount(player.getName());
        double balance = account == null ? 0.0D : account.getBalance();
        FeeState state = evaluate(balance, totalDaily);
        if (state == FeeState.DEBT) {
            Land debtLand = account == null
                    ? null : lands.getById(account.getDebtLandId());
            lines.add(plugin.getMessages().gameRaw(
                    "fees.rent-debt-v3",
                    "&cGlobal rent account is overdue at {balance}. Unpaid rent belongs to {land}. Deposit with /land rent deposit <amount>.",
                    "land", debtLand == null ? "-" : debtLand.getDisplayName(),
                    "balance", plugin.getEconomy().format(balance)));
        } else if (state == FeeState.LOW) {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.rent-low-v3",
                    "&eGlobal rent account={balance}, total rent={daily}/day across {lands} lands, only {days} days remain.",
                    "balance", plugin.getEconomy().format(balance),
                    "daily", plugin.getEconomy().format(totalDaily),
                    "lands", Integer.toString(rentedLands.size()),
                    "days", coveredDays(balance, totalDaily)));
        } else {
            lines.add(plugin.getMessages().gameRaw(
                    "fees.rent-status-v3",
                    "&7Global rent account={balance}, total rent={daily}/day across {lands} lands, covers {days} days.",
                    "balance", plugin.getEconomy().format(balance),
                    "daily", plugin.getEconomy().format(totalDaily),
                    "lands", Integer.toString(rentedLands.size()),
                    "days", coveredDays(balance, totalDaily)));
        }
    }

    public void runMinuteCheck() {
        Map<String, Player> online = new LinkedHashMap<String, Player>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.put(Text.key(player.getName()), player);
        }
        Map<String, List<String>> notices = new LinkedHashMap<String, List<String>>();
        Map<String, Double> dailyRent = new LinkedHashMap<String, Double>();
        for (Land land : lands.getLands()) {
            if (!land.isSubclaim()) {
                appendMaintenanceWarning(notices, land);
            }
            if (land.isRentEnabled()) {
                for (String playerKey : lands.getRentPayers(land)) {
                    if (!online.containsKey(playerKey)) {
                        continue;
                    }
                    Double current = dailyRent.get(playerKey);
                    dailyRent.put(playerKey, Double.valueOf((current == null
                            ? 0.0D : current.doubleValue())
                            + land.getRentAmount()));
                }
            }
        }
        appendRentWarnings(notices, online, dailyRent);
        for (Map.Entry<String, List<String>> entry : notices.entrySet()) {
            Player player = online.get(entry.getKey());
            if (player == null) {
                continue;
            }
            for (String message : entry.getValue()) {
                player.sendMessage(message);
            }
        }
    }

    private void appendMaintenanceWarning(Map<String, List<String>> notices, Land land) {
        if (land.isMaintenanceExempt()) {
            return;
        }
        Player owner = plugin.getServer().getPlayerExact(land.getOwner());
        if (owner == null) {
            return;
        }
        double maintenance = plugin.getMaintenanceService().getMaintenanceFee(land);
        int intervalDays = plugin.getLevels().getProfile(land)
                .getMaintenanceIntervalDays();
        double balance = land.getBankBalance();
        FeeState state = evaluateMaintenance(balance, maintenance);
        if (state != FeeState.DEBT && state != FeeState.LOW) {
            return;
        }
        String message = state == FeeState.DEBT
                ? plugin.getMessages().gameRaw(
                "fees.maintenance-debt-warning-v3",
                "&c[Land fees] {land}: land bank is in debt at {balance}; maintenance is {maintenance}/{days} days. Deposit with /land bank deposit <amount> {land}.",
                "land", land.getDisplayName(),
                "balance", plugin.getEconomy().format(balance),
                "maintenance", plugin.getEconomy().format(maintenance),
                "days", Integer.toString(intervalDays))
                : plugin.getMessages().gameRaw(
                "fees.maintenance-low-warning-v3",
                "&e[Land fees] {land}: land bank={balance}; the next maintenance charge is {required}/{days} days. Deposit with /land bank deposit <amount> {land}.",
                "land", land.getDisplayName(),
                "balance", plugin.getEconomy().format(balance),
                "required", plugin.getEconomy().format(maintenance),
                "days", Integer.toString(intervalDays));
        addNotice(notices, Text.key(owner.getName()), message);
    }

    private void appendRentWarnings(Map<String, List<String>> notices,
                                    Map<String, Player> online,
                                    Map<String, Double> dailyRent) {
        for (Map.Entry<String, Double> entry : dailyRent.entrySet()) {
            String playerKey = entry.getKey();
            Player player = online.get(playerKey);
            if (player == null) {
                continue;
            }
            RentAccount account = lands.getRentAccount(playerKey);
            double balance = account == null ? 0.0D : account.getBalance();
            double totalDaily = entry.getValue().doubleValue();
            FeeState state = evaluate(balance, totalDaily);
            if (state != FeeState.DEBT && state != FeeState.LOW) {
                continue;
            }
            String message = state == FeeState.DEBT
                    ? plugin.getMessages().gameRaw(
                    "fees.rent-debt-warning-v3",
                    "&c[Land fees] Global rent account is overdue at {balance}; unpaid rent belongs to {land}. Deposit with /land rent deposit <amount>.",
                    "land", debtLandName(account),
                    "balance", plugin.getEconomy().format(balance),
                    "daily", plugin.getEconomy().format(totalDaily))
                    : plugin.getMessages().gameRaw(
                    "fees.rent-low-warning-v3",
                    "&e[Land fees] Global rent account={balance}; 7 days of total rent costs {required}. Deposit with /land rent deposit <amount>.",
                    "balance", plugin.getEconomy().format(balance),
                    "required", plugin.getEconomy().format(
                            totalDaily * RENT_WARNING_DAYS));
            addNotice(notices, playerKey, message);
        }
    }

    private double totalDailyRent(List<Land> rentedLands) {
        double total = 0.0D;
        for (Land land : rentedLands) {
            total += land.getRentAmount();
        }
        return total;
    }

    private String debtLandName(RentAccount account) {
        Land land = account == null
                ? null : lands.getById(account.getDebtLandId());
        return land == null ? "-" : land.getDisplayName();
    }

    static FeeState evaluate(double balance, double dailyFee) {
        if (dailyFee <= 0.0D) {
            return FeeState.FREE;
        }
        if (balance < 0.0D) {
            return FeeState.DEBT;
        }
        if (balance < dailyFee * RENT_WARNING_DAYS) {
            return FeeState.LOW;
        }
        return FeeState.OK;
    }

    static FeeState evaluateMaintenance(double balance, double maintenanceFee) {
        if (maintenanceFee <= 0.0D) {
            return FeeState.FREE;
        }
        if (balance < 0.0D) {
            return FeeState.DEBT;
        }
        return balance < maintenanceFee ? FeeState.LOW : FeeState.OK;
    }

    static String coveredDays(double balance, double dailyFee) {
        if (dailyFee <= 0.0D) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f",
                Math.max(0.0D, balance) / dailyFee);
    }

    static String coveredMaintenanceCycles(double balance,
                                           double maintenanceFee) {
        if (maintenanceFee <= 0.0D) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f",
                Math.max(0.0D, balance) / maintenanceFee);
    }

    private void addNotice(Map<String, List<String>> notices, String playerKey,
                           String message) {
        List<String> messages = notices.get(playerKey);
        if (messages == null) {
            messages = new ArrayList<String>();
            notices.put(playerKey, messages);
        }
        messages.add(message);
    }

    enum FeeState {
        FREE,
        DEBT,
        LOW,
        OK
    }
}
