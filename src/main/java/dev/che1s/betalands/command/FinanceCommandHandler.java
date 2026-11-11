package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;

import dev.che1s.betalands.BetaLandsPlugin;

/**
 * Stable finance command facade. Bank and rent workflows live in focused
 * handlers so each command family can evolve and be tested independently.
 */
final class FinanceCommandHandler {
    private final LandBankCommandHandler bankCommands;
    private final RentCommandHandler rentCommands;

    FinanceCommandHandler(BetaLandsPlugin plugin) {
        this.bankCommands = new LandBankCommandHandler(plugin);
        this.rentCommands = new RentCommandHandler(plugin);
    }

    boolean bank(CommandSender sender, String[] args) {
        return bankCommands.bank(sender, args);
    }

    boolean bank(CommandSender sender, String[] args, boolean force) {
        return bankCommands.bank(sender, args, force);
    }

    boolean bankAll(CommandSender sender, String[] args) {
        return bankCommands.bankAll(sender, args);
    }

    boolean rent(CommandSender sender, String[] args) {
        return rentCommands.rent(sender, args);
    }
}
