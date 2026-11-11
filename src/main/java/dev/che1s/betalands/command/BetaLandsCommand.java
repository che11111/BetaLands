package dev.che1s.betalands.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.permission.PermissionNodes;

/**
 * Routes /land to focused player, finance and administration handlers.
 */
public final class BetaLandsCommand implements CommandExecutor {
    private final BetaLandsPlugin plugin;
    private final PlayerCommandHandler playerCommands;
    private final FinanceCommandHandler financeCommands;
    private final AdminCommandHandler adminCommands;
    private final AdminLandCommandHandler adminLandCommands;

    public BetaLandsCommand(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.playerCommands = new PlayerCommandHandler(plugin);
        this.financeCommands = new FinanceCommandHandler(plugin);
        this.adminCommands = new AdminCommandHandler(plugin, playerCommands);
        this.adminLandCommands = new AdminLandCommandHandler(
                plugin, playerCommands, financeCommands);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (sender instanceof Player
                && !sender.isOp()
                && !sender.hasPermission(PermissionNodes.USE)
                && !sender.hasPermission(PermissionNodes.ADMIN)) {
            sender.sendMessage(plugin.getMessages().game(
                    "common.no-permission",
                    "&cYou do not have permission."));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            playerCommands.help(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        String group = route(subcommand);
        if ("bank".equals(group)) {
            return financeCommands.bank(sender, args);
        }
        if ("bank-all".equals(group)) {
            return financeCommands.bankAll(sender, args);
        }
        if ("rent".equals(group)) {
            return financeCommands.rent(sender, args);
        }
        if ("admin".equals(group)) {
            return adminCommands.handle(sender, args);
        }
        if ("admin-land".equals(group)) {
            return adminLandCommands.handle(sender, args, false);
        }
        if ("admin-land-force".equals(group)) {
            return adminLandCommands.handle(sender, args, true);
        }
        if ("player".equals(group)
                && playerCommands.handle(subcommand, sender, args)) {
            return true;
        }
        sender.sendMessage(plugin.getMessages().game(
                "common.unknown-command",
                "&cUnknown command. Use /land help."));
        return true;
    }

    static String route(String subcommand) {
        String value = subcommand == null ? ""
                : subcommand.toLowerCase(java.util.Locale.ROOT);
        if ("bank".equals(value)) {
            return "bank";
        }
        if ("bank-all".equals(value)) {
            return "bank-all";
        }
        if ("rent".equals(value)) {
            return "rent";
        }
        if ("admin".equals(value)) {
            return "admin";
        }
        if ("admin-land".equals(value)) {
            return "admin-land";
        }
        if ("admin-land-force".equals(value)) {
            return "admin-land-force";
        }
        if (value.length() == 0) {
            return "unknown";
        }
        return "player";
    }
}
