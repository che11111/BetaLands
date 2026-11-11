package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.Text;

final class RentCommandHandler extends FinanceCommandSupport {
    RentCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean rent(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.RENT_USE)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            rentUsage(sender);
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("deposit".equals(action)) {
            if (!validDepositSyntax(args)) {
                rentUsage(sender);
                return true;
            }
            return depositRent(player, args[2]);
        }
        if ("withdraw".equals(action)) {
            if (!validWithdrawSyntax(args)) {
                rentUsage(sender);
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
            return withdrawRent(player, amount);
        }
        if ("info".equals(action)) {
            if (args.length > 3) {
                rentUsage(sender);
                return true;
            }
            Land rentedLand = args.length == 3 ? resolve(sender, args[2])
                    : resolveRentContextLand(player);
            if (rentedLand == null) {
                if (args.length == 3) {
                    sendContextLandError(sender, args[2]);
                } else {
                    showGlobalRentInfo(sender, player);
                }
                return true;
            }
            if (!canViewRent(player, rentedLand)) {
                msg(sender, "rent.info-denied",
                        "&cOnly the main-land owner, an admin, or a rent-paying member may view this rent account.");
                return true;
            }
            sendRentInfo(sender, rentedLand);
            return true;
        }
        if ("set".equals(action)) {
            if (args.length < 3 || args.length > 4) {
                rentUsage(sender);
                return true;
            }
            Land rentedLand = args.length == 4 ? resolve(sender, args[3])
                    : resolveManageableContextLand(player);
            if (!canConfigureRent(player, rentedLand)) {
                msg(sender, "rent.configure-denied-v2",
                        "&cOnly the main-land owner or an admin may configure rent.");
                return true;
            }
            Double parsedAmount = parseAmount(sender, args[2]);
            if (parsedAmount == null) {
                return true;
            }
            double amount = parsedAmount.doubleValue();
            if (amount == 0.0D) {
                rentedLand.setRentEnabled(false);
                rentedLand.setRentAmount(0.0D);
                plugin.requestSaveAsync(true);
                msg(sender, "rent.disabled-v2",
                        "&eRent is now disabled for {land}.",
                        "land", rentedLand.getDisplayName());
                auditLand(sender, "RENT_SET", null, rentedLand,
                        "Daily rent=0.00, rent disabled");
                return true;
            }
            boolean wasEnabled = rentedLand.isRentEnabled();
            if (!wasEnabled) {
                rentedLand.setLastRentAt(System.currentTimeMillis());
            }
            rentedLand.setRentAmount(amount);
            rentedLand.setRentEnabled(true);
            auditLand(sender, "RENT_SET", null, rentedLand,
                    "Daily rent=" + plugin.getEconomy().format(amount)
                            + ", charged to members with rent permission");
            plugin.requestSaveAsync(true);
            msg(sender, "rent.set-v2",
                    "&aSet daily rent for {land} to {amount}. Members with rent permission pay it; the owner is excluded.",
                    "land", rentedLand.getDisplayName(),
                    "amount", plugin.getEconomy().format(amount));
            return true;
        }
        rentUsage(sender);
        return true;
    }

    private boolean depositRent(Player player, String amountText) {
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
        RentAccount account = plugin.getLandManager()
                .getRentAccount(player.getName());
        String playerKey = Text.key(player.getName());
        boolean hasRentObligation = false;
        for (Land land : plugin.getLandManager()
                .getAccessibleLands(player.getName())) {
            if (land.isRentEnabled() && plugin.getLandManager()
                    .getRentPayers(land).contains(playerKey)) {
                hasRentObligation = true;
                break;
            }
        }
        boolean debtTarget = account != null
                && (account.getDebtLandId() != null
                || account.getBalance() < 0.0D);
        if (!hasRentObligation && !debtTarget) {
            msg(player, "rent.deposit-no-obligation",
                    "&cYou have no active rent obligation or unpaid rent.");
            return true;
        }
        if (!plugin.getEconomy().isAvailable()) {
            msg(player, "common.no-economy",
                    "&cThe server does not have a usable iConomy economy.");
            return true;
        }
        dev.che1s.betalands.economy.EconomyService.PaymentResult payment =
                plugin.getEconomy().debitAlways(player, amount, "rent deposit");
        if (!payment.isSuccess()) {
            msg(player, "common.payment-failed", "&c{reason}",
                    "reason", payment.getError());
            return true;
        }
        if (account == null) {
            account = plugin.getLandManager()
                    .getOrCreateRentAccount(player.getName());
        }
        double debtPaid = dev.che1s.betalands.service.LandMaintenanceService
                .debtSettledByDeposit(amount, account.getBalance());
        account.addBalance(amount);
        Land debtLand = plugin.getLandManager()
                .getById(account.getDebtLandId());
        Land bankLand = debtLand == null ? null
                : plugin.getMaintenanceService().getBankLand(debtLand);
        if (bankLand != null && debtPaid > 0.0D) {
            plugin.getMaintenanceService().creditLandBank(bankLand, debtPaid);
        }
        if (account.getBalance() >= 0.0D) {
            account.setDebtLandId(null);
        }
        auditEconomy(player, "RENT_DEPOSIT", debtLand, amount,
                account.getBalance(), "Rent deposit for " + player.getName()
                        + ", debt paid=" + plugin.getEconomy().format(debtPaid));
        plugin.requestSaveAsync(true);
        msg(player, "rent.deposit-success-global",
                "&aDeposited {amount} into your global rent account. Prepaid rent={balance}.",
                "amount", plugin.getEconomy().format(amount),
                "balance", plugin.getEconomy().format(account.getBalance()));
        return true;
    }

    private boolean withdrawRent(Player player, double amount) {
        RentAccount account = plugin.getLandManager()
                .getRentAccount(player.getName());
        if (account == null || account.getBalance() <= 0.0D
                || account.getBalance() < amount) {
            msg(player, "rent.insufficient",
                    "&cYour prepaid rent account does not have enough money.");
            return true;
        }
        dev.che1s.betalands.economy.EconomyService.PaymentResult payment =
                plugin.getEconomy().creditAlways(player, amount);
        if (!payment.isSuccess()) {
            msg(player, "common.payment-failed", "&c{reason}",
                    "reason", payment.getError());
            return true;
        }
        account.addBalance(-amount);
        auditEconomy(player, "RENT_WITHDRAW", null, amount,
                account.getBalance(), "Rent withdrawal for " + player.getName());
        plugin.requestSaveAsync(true);
        msg(player, "rent.withdraw-success-global",
                "&aWithdrew {amount} from your global rent account. Prepaid rent={balance}.",
                "amount", plugin.getEconomy().format(amount),
                "balance", plugin.getEconomy().format(account.getBalance()));
        return true;
    }

    private boolean canViewRent(Player player, Land land) {
        return canConfigureRent(player, land)
                || plugin.getLandManager().getRentPayers(land)
                .contains(Text.key(player.getName()))
                || plugin.getMaintenanceService()
                .isRentInDebt(land, player.getName());
    }

    private Land resolveRentContextLand(Player player) {
        Land current = plugin.getLandManager().findLandAt(player.getLocation());
        if (current != null && canViewRent(player, current)) {
            return current;
        }
        List<Land> candidates = new ArrayList<Land>();
        for (Land land : plugin.getLandManager().getLands()) {
            if (canViewRent(player, land)) {
                candidates.add(land);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    static boolean validDepositSyntax(String[] args) {
        return args != null && args.length == 3
                && args[0] != null
                && "rent".equalsIgnoreCase(args[0])
                && args[1] != null
                && "deposit".equalsIgnoreCase(args[1])
                && args[2] != null && args[2].trim().length() > 0;
    }

    static boolean validWithdrawSyntax(String[] args) {
        return args != null && args.length == 3
                && args[0] != null
                && "rent".equalsIgnoreCase(args[0])
                && args[1] != null
                && "withdraw".equalsIgnoreCase(args[1])
                && args[2] != null && args[2].trim().length() > 0;
    }

    private void showGlobalRentInfo(CommandSender sender, Player player) {
        RentAccount account = plugin.getLandManager()
                .getRentAccount(player.getName());
        int rentedLands = 0;
        double totalDaily = 0.0D;
        for (Land land : plugin.getLandManager().getLands()) {
            if (land.isRentEnabled() && plugin.getLandManager()
                    .getRentPayers(land)
                    .contains(Text.key(player.getName()))) {
                rentedLands++;
                totalDaily += land.getRentAmount();
            }
        }
        Land debtLand = account == null ? null
                : plugin.getLandManager().getById(account.getDebtLandId());
        msg(sender, "rent.global-info",
                "&6Global rent account | Balance={balance} | Rented lands={lands} | Total daily rent={daily} | Debt land={debtLand}",
                "balance", plugin.getEconomy().format(
                        account == null ? 0.0D : account.getBalance()),
                "lands", Integer.toString(rentedLands),
                "daily", plugin.getEconomy().format(totalDaily),
                "debtLand", debtLand == null ? "-" : debtLand.getDisplayName());
    }

    private boolean canConfigureRent(Player player, Land rentedLand) {
        if (rentedLand == null) {
            return false;
        }
        Land main = plugin.getMaintenanceService().getBankLand(rentedLand);
        return main != null && (plugin.getLandManager().isAdmin(player)
                || main.isOwner(player.getName()));
    }

    private void sendRentInfo(CommandSender sender, Land rentedLand) {
        msg(sender, "rent.info-header", "&6{land} rent",
                "land", rentedLand.getDisplayName());
        msg(sender, "rent.info-status-v2",
                "&7Status={state} | Daily rent={amount} | Paying members={players}",
                "state", rentedLand.isRentEnabled() ? "enabled" : "disabled",
                "amount", plugin.getEconomy().format(rentedLand.getRentAmount()),
                "players", Integer.toString(plugin.getLandManager()
                        .getRentPayers(rentedLand).size()));
        msg(sender, "rent.info-next", "&7Next charge: {time}",
                "time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(
                        new java.util.Date(plugin.getMaintenanceService()
                                .getNextRentAt(rentedLand))));
        if (sender instanceof Player) {
            Player player = (Player) sender;
            RentAccount own = plugin.getLandManager()
                    .getRentAccount(player.getName());
            if (own != null) {
                msg(sender, "rent.info-balance",
                        "&7Your prepaid rent: {balance}",
                        "balance", plugin.getEconomy().format(own.getBalance()));
                if (plugin.getMaintenanceService()
                        .isRentInDebt(rentedLand, player.getName())) {
                    Land debtLand = plugin.getLandManager()
                            .getById(own.getDebtLandId());
                    msg(sender, "rent.info-debt",
                            "&cUnpaid rent belongs to {land}.",
                            "land", debtLand == null ? "-"
                                    : debtLand.getDisplayName());
                }
            }
        }
        Set<String> keys = plugin.getLandManager()
                .getRentPayers(rentedLand);
        if (!keys.isEmpty()) {
            msg(sender, "rent.info-payers", "&7Payers:");
            for (String key : keys) {
                RentAccount account = plugin.getLandManager().getRentAccount(key);
                if (account != null) {
                    msg(sender, "rent.info-entry", "&7  {player}: {balance}",
                            "player", account.getPlayerName(),
                            "balance", plugin.getEconomy().format(
                                    account.getBalance()));
                }
            }
        }
    }

    private void rentUsage(CommandSender sender) {
        msg(sender, "rent.usage-v2",
                "&eUsage: /land rent set <amount> [land] | /land rent deposit <amount> | /land rent withdraw <amount> | /land rent info [land]");
    }
}
