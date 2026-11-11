package dev.che1s.betalands.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.che1s.betalands.BetaLandsPlugin;

public final class EconomyService {
    private final BetaLandsPlugin plugin;
    private IConomyProvider provider;
    private String hookState = "not-initialized";

    public EconomyService(BetaLandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        provider = null;
        if (!plugin.getSettings().isEconomyEnabled()) {
            hookState = "disabled-in-config";
            return;
        }
        Plugin economyPlugin = findPlugin("iConomy");
        if (economyPlugin == null || !economyPlugin.isEnabled()) {
            hookState = economyPlugin == null ? "plugin-not-found" : "plugin-disabled";
            plugin.getMessages().warning("startup.economy-missing",
                    "iConomy is unavailable; charged operations are disabled.");
            return;
        }
        try {
            provider = new IConomyProvider();
            if (!provider.isReady()) {
                provider = null;
                hookState = "api-not-ready";
                plugin.getMessages().warning("startup.economy-missing",
                        "iConomy is unavailable; charged operations are disabled.");
            } else {
                hookState = "ready";
                plugin.getMessages().info("startup.economy-hook", "Hooked iConomy economy.");
            }
        } catch (RuntimeException exception) {
            provider = null;
            hookState = "hook-failed: " + exception;
            plugin.getMessages().warning("economy.hook-failed",
                    "Could not hook iConomy: {error}", "error", exception.getMessage());
        }
    }

    public PaymentResult charge(Player player, double amount, String reason) {
        if (!plugin.getSettings().isEconomyEnabled() || amount <= 0.0D) {
            return PaymentResult.free();
        }
        if (!plugin.getSettings().shouldChargeAdmins()
                && plugin.getLandManager().isAdmin(player)) {
            return PaymentResult.bypassed();
        }
        if (provider == null) {
            if (plugin.getSettings().isEconomyRequired()) {
                return PaymentResult.failure("iConomy is unavailable; cannot charge "
                        + format(amount) + ".");
            }
            return PaymentResult.free();
        }
        String playerName = player.getName();
        if (!provider.hasAccount(playerName)) {
            return PaymentResult.failure("Your iConomy account was not found; cannot charge "
                    + format(amount) + ".");
        }
        if (!provider.hasEnough(playerName, amount)) {
            return PaymentResult.failure("Insufficient funds: required " + format(amount)
                    + ", balance " + format(provider.getBalance(playerName)) + ".");
        }
        try {
            provider.withdraw(playerName, amount);
            return PaymentResult.paid(amount);
        } catch (RuntimeException exception) {
            return PaymentResult.failure("Charge failed: " + exception.getMessage());
        }
    }

    public PaymentResult debitAlways(Player player, double amount, String reason) {
        return debitAccount(player.getName(), amount, reason);
    }

    public PaymentResult debitAccount(String playerName, double amount, String reason) {
        if (!plugin.getSettings().isEconomyEnabled() || amount <= 0.0D) {
            return PaymentResult.free();
        }
        if (provider == null) {
            if (plugin.getSettings().isEconomyRequired()) {
                return PaymentResult.failure("iConomy is unavailable; cannot debit "
                        + format(amount) + ".");
            }
            return PaymentResult.free();
        }
        if (!provider.hasAccount(playerName)) {
            return PaymentResult.failure("Your iConomy account was not found; cannot debit "
                    + format(amount) + ".");
        }
        if (!provider.hasEnough(playerName, amount)) {
            return PaymentResult.failure("Insufficient funds: required " + format(amount)
                    + ", balance " + format(provider.getBalance(playerName)) + ".");
        }
        try {
            provider.withdraw(playerName, amount);
            return PaymentResult.paid(amount);
        } catch (RuntimeException exception) {
            return PaymentResult.failure("Debit failed: " + exception.getMessage());
        }
    }

    public double getAccountBalance(String playerName) {
        return provider == null || playerName == null ? 0.0D
                : provider.getBalance(playerName);
    }

    public PaymentResult creditAlways(Player player, double amount) {
        if (amount <= 0.0D) {
            return PaymentResult.free();
        }
        if (provider == null) {
            return PaymentResult.failure("iConomy is unavailable; cannot withdraw "
                    + format(amount) + ".");
        }
        String playerName = player.getName();
        if (!provider.hasAccount(playerName)) {
            return PaymentResult.failure("Your iConomy account was not found; cannot withdraw "
                    + format(amount) + ".");
        }
        try {
            provider.credit(playerName, amount);
            return PaymentResult.paid(amount);
        } catch (RuntimeException exception) {
            return PaymentResult.failure("Withdrawal failed: " + exception.getMessage());
        }
    }

    public boolean canAfford(Player player, double amount) {
        if (!plugin.getSettings().isEconomyEnabled() || amount <= 0.0D
                || (!plugin.getSettings().shouldChargeAdmins()
                && plugin.getLandManager().isAdmin(player))) {
            return true;
        }
        return provider == null
                ? !plugin.getSettings().isEconomyRequired()
                : provider.hasAccount(player.getName()) && provider.hasEnough(player.getName(), amount);
    }

    public String format(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP);
        return new DecimalFormat("0.00").format(value);
    }

    public static BigDecimal round(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isAvailable() {
        return provider != null;
    }

    public String getProviderName() {
        return provider == null ? "none" : "iConomy";
    }

    public String diagnostic() {
        return provider != null ? "hooked" : "not-hooked(" + hookState + ")";
    }

    private Plugin findPlugin(String name) {
        Plugin direct = plugin.getServer().getPluginManager().getPlugin(name);
        if (direct != null) {
            return direct;
        }
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            if (candidate != null && candidate.getDescription() != null
                    && name.equalsIgnoreCase(candidate.getDescription().getName())) {
                return candidate;
            }
        }
        return null;
    }

    public static final class PaymentResult {
        private final boolean success;
        private final boolean charged;
        private final double amount;
        private final String error;

        private PaymentResult(boolean success, boolean charged, double amount, String error) {
            this.success = success;
            this.charged = charged;
            this.amount = amount;
            this.error = error;
        }

        public static PaymentResult free() {
            return new PaymentResult(true, false, 0.0D, null);
        }

        public static PaymentResult bypassed() {
            return new PaymentResult(true, false, 0.0D, null);
        }

        public static PaymentResult paid(double amount) {
            return new PaymentResult(true, true, amount, null);
        }

        public static PaymentResult failure(String error) {
            return new PaymentResult(false, false, 0.0D, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCharged() {
            return charged;
        }

        public double getAmount() {
            return amount;
        }

        public String getError() {
            return error;
        }
    }
}
