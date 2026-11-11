package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;

final class LandBankCommandHandler extends FinanceCommandSupport {
    LandBankCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean bank(CommandSender sender, String[] args) {
        return bank(sender, args, false);
    }

    boolean bank(CommandSender sender, String[] args, boolean force) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!force && !hasPermission(sender, PermissionNodes.BANK_USE)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            bankUsage(sender);
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("info".equals(action)) {
            if (args.length > 3) {
                bankUsage(sender);
                return true;
            }
            Land land = args.length == 3 ? resolve(sender, args[2])
                    : resolveOwnerBankLand(player);
            Land main = land == null ? null
                    : plugin.getMaintenanceService().getBankLand(land);
            if (main == null) {
                sendContextLandError(sender, args.length == 3 ? args[2] : null);
                return true;
            }
            if (!canUseLandBank(player, main)) {
                msg(sender, "bank.owner-only-v2",
                        "&cOnly the main-land owner or an admin may use this bank.");
                return true;
            }
            showBankInfo(sender, main);
            return true;
        }
        if (!"deposit".equals(action) && !"withdraw".equals(action)) {
            bankUsage(sender);
            return true;
        }
        if (args.length < 3 || args.length > 4) {
            bankUsage(sender);
            return true;
        }
        Land land = args.length == 4 ? resolve(sender, args[3])
                : resolveOwnerBankLand(player);
        Land main = land == null ? null
                : plugin.getMaintenanceService().getBankLand(land);
        if (main == null) {
            sendContextLandError(sender, args.length == 4 ? args[3] : null);
            return true;
        }
        if (!canUseLandBank(player, main)) {
            msg(sender, "bank.owner-only-v2",
                    "&cOnly the main-land owner or an admin may use this bank.");
            return true;
        }
        Double parsedAmount = parseAmount(sender, args[2]);
        if (parsedAmount == null) {
            return true;
        }
        double amount = parsedAmount.doubleValue();
        if (amount <= 0.0D) {
            msg(sender, "common.positive-amount",
                    "&cAmount must be greater than 0.");
            return true;
        }
        return "deposit".equals(action)
                ? depositLandBank(player, main, amount)
                : withdrawLandBank(player, main, amount, force);
    }

    boolean bankAll(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.BANK_USE)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            bankAllUsage(sender);
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("info".equals(action)) {
            if (args.length != 2) {
                bankAllUsage(sender);
                return true;
            }
            showBankAllInfo(sender, player);
            return true;
        }
        if ("deposit".equals(action)) {
            if (args.length != 3) {
                bankAllUsage(sender);
                return true;
            }
            return depositBankAll(player, args[2]);
        }
        if ("withdraw".equals(action)) {
            msg(sender, "bank-all.no-withdraw",
                    "&c/land bank-all does not support withdraw. Withdraw from each land separately.");
            return true;
        }
        bankAllUsage(sender);
        return true;
    }

    private void showBankAllInfo(CommandSender sender, Player player) {
        List<Land> owned = ownedMainLands(player);
        if (owned.isEmpty()) {
            msg(sender, "bank-all.no-lands",
                    "&eYou do not own any main lands.");
            return;
        }
        double totalDaily = 0.0D;
        double totalBalance = 0.0D;
        msg(sender, "bank-all.info-header",
                "&fAll owned land banks ({count}):",
                "count", Integer.toString(owned.size()));
        for (Land land : owned) {
            double daily = plugin.getMaintenanceService().getDailyFee(land);
            totalDaily += daily;
            totalBalance += land.getBankBalance();
            msg(sender, "bank-all.info-land",
                    "&f{land}: daily upkeep={daily}, bank={balance}.",
                    "land", land.getDisplayName(),
                    "daily", plugin.getEconomy().format(daily),
                    "balance", plugin.getEconomy().format(land.getBankBalance()));
        }
        msg(sender, "bank-all.info-total",
                "&fTotal daily upkeep={totalDaily}, total bank={totalBalance}.",
                "totalDaily", plugin.getEconomy().format(totalDaily),
                "totalBalance", plugin.getEconomy().format(totalBalance));
    }

    private boolean depositBankAll(Player player, String amountText) {
        List<Land> owned = ownedMainLands(player);
        if (owned.isEmpty()) {
            msg(player, "bank-all.no-lands",
                    "&eYou do not own any main lands.");
            return true;
        }
        Double parsedAmount = parseAmount(player, amountText);
        if (parsedAmount == null) {
            return true;
        }
        double amount = parsedAmount.doubleValue();
        if (amount <= 0.0D) {
            msg(player, "common.positive-amount",
                    "&cAmount must be greater than 0.");
            return true;
        }
        if (!plugin.getEconomy().isAvailable()) {
            msg(player, "common.no-economy",
                    "&cThe server does not have a usable iConomy economy.");
            return true;
        }
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        double totalWeight = 0.0D;
        for (Land land : owned) {
            double daily = plugin.getMaintenanceService().getDailyFee(land);
            weights.put(land.getId(), Double.valueOf(daily));
            totalWeight += daily;
        }
        if (totalWeight <= 0.0D) {
            msg(player, "bank-all.no-maintenance",
                    "&eYour owned lands have no chargeable maintenance fees, so proportional distribution is unavailable.");
            return true;
        }
        dev.che1s.betalands.economy.EconomyService.PaymentResult payment =
                plugin.getEconomy().debitAlways(player, amount, "bank-all deposit");
        if (!payment.isSuccess()) {
            msg(player, "common.payment-failed", "&c{reason}",
                    "reason", payment.getError());
            return true;
        }
        Map<String, Double> shares =
                dev.che1s.betalands.service.FinanceService
                        .distributeProportionally(amount, weights);
        List<String> breakdownParts = new ArrayList<String>();
        double deposited = 0.0D;
        for (Land land : owned) {
            double share = shares.get(land.getId()) == null
                    ? 0.0D : shares.get(land.getId()).doubleValue();
            if (share <= 0.0D) {
                continue;
            }
            deposited += share;
            double debtPaid = plugin.getMaintenanceService()
                    .creditLandBank(land, share);
            breakdownParts.add(org.bukkit.ChatColor.AQUA + land.getDisplayName()
                    + org.bukkit.ChatColor.WHITE + " "
                    + org.bukkit.ChatColor.GREEN + "+"
                    + plugin.getEconomy().format(share)
                    + org.bukkit.ChatColor.WHITE);
            auditEconomy(player, "BANK_ALL_DEPOSIT", land, share,
                    land.getBankBalance(), "Proportional bank-all deposit, debt paid="
                            + plugin.getEconomy().format(debtPaid));
        }
        plugin.requestSaveAsync(true);
        msg(player, "bank-all.deposit-success",
                "&fDeposited {amount} across {count} lands: {breakdown}.",
                "amount", plugin.getEconomy().format(deposited),
                "count", Integer.toString(breakdownParts.size()),
                "breakdown", String.join(", ", breakdownParts));
        return true;
    }

    private List<Land> ownedMainLands(Player player) {
        List<Land> owned = new ArrayList<Land>(
                plugin.getLandManager().getOwnedMainLands(player.getName()));
        java.util.Collections.sort(owned, new java.util.Comparator<Land>() {
            @Override
            public int compare(Land first, Land second) {
                return first.getDisplayName().compareToIgnoreCase(
                        second.getDisplayName());
            }
        });
        return owned;
    }

    private void bankAllUsage(CommandSender sender) {
        msg(sender, "bank-all.usage",
                "&eUsage: /land bank-all info | /land bank-all deposit <amount>");
    }

    private void showBankInfo(CommandSender sender, Land main) {
        LevelProfile profile = plugin.getLevels().getProfile(main);
        msg(sender, "bank.header", "&6{land} land bank",
                "land", main.getDisplayName());
        msg(sender, "bank.balance-v5",
                "&7Balance={balance} | Server upkeep={daily}/day | Charge every={days} days | Level={level}",
                "balance", plugin.getEconomy().format(main.getBankBalance()),
                "daily", plugin.getEconomy().format(
                        plugin.getMaintenanceService().getDailyFee(main)),
                "days", Integer.toString(profile.getMaintenanceIntervalDays()),
                "level", profile.getDisplayName());
        msg(sender, "bank.next", "&7Next charge: {time}",
                "time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(
                        new java.util.Date(
                                plugin.getMaintenanceService().getNextDueAt(main))));
        if (main.isMaintenanceExempt()) {
            msg(sender, "bank.maintenance-exempt",
                    "&aMaintenance: exempt");
        }
        if (main.getBankBalance() < 0.0D) {
            msg(sender, "bank.debt-v2",
                    "&cStatus: in debt. Server upkeep will continue to increase the debt until you deposit.");
        } else {
            msg(sender, "bank.normal", "&aStatus: normal");
        }
    }

    private boolean depositLandBank(Player player, Land main, double amount) {
        if (!plugin.getEconomy().isAvailable()) {
            msg(player, "common.no-economy",
                    "&cThe server does not have a usable iConomy economy.");
            return true;
        }
        dev.che1s.betalands.economy.EconomyService.PaymentResult payment =
                plugin.getEconomy().debitAlways(player, amount,
                        "land bank deposit");
        if (!payment.isSuccess()) {
            msg(player, "common.payment-failed", "&c{reason}",
                    "reason", payment.getError());
            return true;
        }
        double debtPaid = plugin.getMaintenanceService()
                .creditLandBank(main, amount);
        auditEconomy(player, "BANK_DEPOSIT", main, amount,
                main.getBankBalance(), "Deposited into land bank, server debt paid="
                        + plugin.getEconomy().format(debtPaid));
        plugin.requestSaveAsync(true);
        msg(player, "bank.deposit-success-v2",
                "&aDeposited {amount} into {land}. Bank balance={balance}.",
                "amount", plugin.getEconomy().format(amount),
                "land", main.getDisplayName(),
                "balance", plugin.getEconomy().format(main.getBankBalance()));
        return true;
    }

    private boolean withdrawLandBank(Player player, Land main, double amount,
                                     boolean force) {
        if (!force && (main.getBankBalance() <= 0.0D
                || main.getBankBalance() < amount)) {
            msg(player, "bank.insufficient",
                    "&cThe land bank does not have enough money.");
            return true;
        }
        dev.che1s.betalands.economy.EconomyService.PaymentResult payment =
                plugin.getEconomy().creditAlways(player, amount);
        if (!payment.isSuccess()) {
            msg(player, "common.payment-failed", "&c{reason}",
                    "reason", payment.getError());
            return true;
        }
        main.addBankBalance(-amount);
        auditEconomy(player, "BANK_WITHDRAW", main, amount,
                main.getBankBalance(), force
                        ? "Force withdrew from land bank; overdraw allowed"
                        : "Withdrew from land bank");
        plugin.requestSaveAsync(true);
        msg(player, "bank.withdraw-success-v2",
                "&aWithdrew {amount} from {land}. Bank balance={balance}.",
                "amount", plugin.getEconomy().format(amount),
                "land", main.getDisplayName(),
                "balance", plugin.getEconomy().format(main.getBankBalance()));
        return true;
    }

    private boolean canUseLandBank(Player player, Land main) {
        return plugin.getLandManager().isAdmin(player)
                || main.isOwner(player.getName());
    }

    private Land resolveOwnerBankLand(Player player) {
        Land current = plugin.getLandManager().findLandAt(player.getLocation());
        if (current != null) {
            Land main = plugin.getMaintenanceService().getBankLand(current);
            if (main != null && canUseLandBank(player, main)) {
                return main;
            }
        }
        return resolveSingleOwnedMain(player);
    }

    private void bankUsage(CommandSender sender) {
        msg(sender, "bank.usage-v3",
                "&eUsage: /land bank info [land] | /land bank deposit|withdraw <amount> [land]");
    }
}
