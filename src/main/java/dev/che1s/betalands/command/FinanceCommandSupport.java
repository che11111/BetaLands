package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;

import dev.che1s.betalands.BetaLandsPlugin;

abstract class FinanceCommandSupport extends CommandSupport {
    FinanceCommandSupport(BetaLandsPlugin plugin) {
        super(plugin);
    }

    protected Double parseAmount(CommandSender sender, String text) {
        dev.che1s.betalands.util.MoneyAmountParser.Result result =
                dev.che1s.betalands.util.MoneyAmountParser.parse(
                        text, plugin.getSettings().getMaxMoneyDecimalPlaces());
        if (result.getStatus()
                == dev.che1s.betalands.util.MoneyAmountParser.Status.TOO_MANY_DECIMALS) {
            msg(sender, "common.decimal-too-many",
                    "&cAmount supports at most {places} decimal places.",
                    "places", Integer.toString(
                            plugin.getSettings().getMaxMoneyDecimalPlaces()));
            return null;
        }
        if (result.getStatus()
                != dev.che1s.betalands.util.MoneyAmountParser.Status.SUCCESS) {
            msg(sender, "common.invalid-amount", "&cInvalid amount.");
            return null;
        }
        return Double.valueOf(result.getAmount());
    }
}
