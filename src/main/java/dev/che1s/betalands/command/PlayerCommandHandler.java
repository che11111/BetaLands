package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.permission.PermissionNodes;

/**
 * Routes non-financial /land commands to focused handlers.
 */
final class PlayerCommandHandler extends CommandSupport {
    private final ClaimCommandHandler claims;
    private final MembershipCommandHandler membership;
    private final ProtectionCommandHandler protection;
    private final TeleportCommandHandler teleport;
    private final OwnershipCommandHandler ownership;
    private final InfoCommandHandler info;

    PlayerCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
        this.claims = new ClaimCommandHandler(plugin);
        this.membership = new MembershipCommandHandler(plugin);
        this.protection = new ProtectionCommandHandler(plugin);
        this.teleport = new TeleportCommandHandler(plugin);
        this.ownership = new OwnershipCommandHandler(plugin);
        this.info = new InfoCommandHandler(plugin);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args) {
        return handle(subcommand, sender, args, false);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args,
                   boolean force) {
        if (claims.handles(subcommand)) {
            return claims.handle(subcommand, sender, args, force);
        }
        if (membership.handles(subcommand)) {
            return membership.handle(subcommand, sender, args, force);
        }
        if (protection.handles(subcommand)) {
            return protection.handle(subcommand, sender, args);
        }
        if (teleport.handles(subcommand)) {
            return teleport.handle(subcommand, sender, args);
        }
        if (ownership.handles(subcommand)) {
            return ownership.handle(subcommand, sender, args);
        }
        if (info.handles(subcommand)) {
            return info.handle(subcommand, sender, args);
        }
        return false;
    }

    void showLevel(CommandSender sender, Player target) {
        info.showLevel(sender, target);
    }

    void showLevel(CommandSender sender, String playerName) {
        info.showLevel(sender, playerName);
    }

    void help(CommandSender sender) {
        msg(sender, "help.header", "&6BetaLands commands");
        msg(sender, "help.land",
                "&e/land create <name> &7| /land claim [land] &7| /land claim auto [land]");
        msg(sender, "help.subclaim",
                "&e/land pos1 &7| /land pos2 &7| /land subcreate <name>");
        msg(sender, "help.member-v3",
                "&e/land add|kick <player> [land] &7| /land accept|deny <land> &7| /land leave [land]");
        msg(sender, "help.ownership-v2",
                "&e/land rename <new-name> [land] &7| /land setowner <player> [land]");
        msg(sender, "help.permission-v2",
                "&e/land perm <player> <permission> <allow|deny> [land]");
        msg(sender, "help.flags",
                "&e/land flags [land] &7| /land flag <flag> <allow|deny|default> [land]");
        msg(sender, "help.bank-v4",
                "&e/land bank info [land] &7| /land bank deposit|withdraw <amount> [land] &7| /land bank-all info|deposit <amount>");
        msg(sender, "help.rent-v3",
                "&e/land rent set <amount> [land] &7| /land rent deposit|withdraw <amount> &7| /land rent info [land]");
        msg(sender, "help.location-v2",
                "&e/land setspawn [land] &7| /land spawn [land] &7| /land tp [land]");
        msg(sender, "help.info-v2",
                "&e/land list &7| /land info [land] &7| /land logs [count] [land] &7| /land level");
        if (sender instanceof Player) {
            LevelProfile profile =
                    plugin.getLevels().getProfile((Player) sender);
            msg(sender, "help.level-v2",
                    "&7Level={level}: main={main}, chunks/main={chunks}, subclaims/main={sub}, family={family}, joined={joined}",
                    "level", profile.getDisplayName(),
                    "main", Integer.toString(profile.getMaxMainLands()),
                    "chunks", Integer.toString(profile.getMaxMainChunks()),
                    "sub", Integer.toString(profile.getMaxSubclaimsPerMain()),
                    "family", Integer.toString(profile.getMaxFamilyMembers()),
                    "joined", Integer.toString(profile.getMaxJoinedLands()));
        }
        if (sender.isOp() || sender.hasPermission(PermissionNodes.ADMIN)) {
            msg(sender, "help.admin-v5",
                    "&d/land admin tp <x> <y> <z> [world] &7| level &7| refresh-map &7| setlevel &7| reload|save|status|logs");
            msg(sender, "help.admin-land-v1",
                    "&d/land admin-land <command> ... &7| /land admin-land-force add <player> <land>");
        }
        msg(sender, "help.permissions",
                "&7Permissions: build, break, use, container, rent, pvp, manage, all");
    }
}
