package dev.che1s.betalands.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.service.FlagChangeResult;
import dev.che1s.betalands.service.LandManager;

final class ProtectionCommandHandler extends CommandSupport {
    ProtectionCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "flags".equals(subcommand) || "flag".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args) {
        if ("flags".equals(subcommand)) {
            return listFlags(sender, args);
        }
        if ("flag".equals(subcommand)) {
            return setFlag(sender, args);
        }
        return false;
    }

    private boolean listFlags(CommandSender sender, String[] args) {
        Land land;
        if (args.length >= 2) {
            land = resolve(sender, args[1]);
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            land = plugin.getLandManager().findLandAt(player.getLocation());
            if (land == null) {
                land = resolveSingleOwnedMain(player);
            }
        } else {
            land = null;
        }
        if (land == null) {
            msg(sender, "flag.list-usage",
                    "&eStand inside a land or use /land flags <land>.");
            return true;
        }
        msg(sender, "flag.list-header",
                "&6{land} protection flags", "land", land.getDisplayName());
        for (LandFlag flag : LandFlag.values()) {
            msg(sender, "flag.list-entry",
                    "&e  {flag} &7= {state} (source: {source})",
                    "flag", flag.getId(),
                    "state", plugin.getLandManager().getEffectiveLandFlag(land, flag)
                            ? "allow" : "deny",
                    "source", plugin.getLandManager().getLandFlagSource(land, flag));
        }
        return true;
    }

    private boolean setFlag(CommandSender sender, String[] args) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.LAND_FLAGS)) {
            noPermission(sender);
            return true;
        }
        Land land;
        String flagText;
        String valueText;
        if (args.length == 3) {
            land = plugin.getLandManager().findLandAt(actor.getLocation());
            if (land == null) {
                land = resolveSingleOwnedMain(actor);
            }
            flagText = args[1];
            valueText = args[2];
        } else if (args.length >= 4) {
            land = resolve(sender, args[3]);
            flagText = args[1];
            valueText = args[2];
        } else {
            msg(sender, "flag.usage",
                    "&eUsage: /land flag <flag> <allow|deny|default> [land]");
            return true;
        }
        if (land == null) {
            sendContextLandError(sender, args.length >= 4 ? args[3] : null);
            return true;
        }
        LandFlag flag = LandFlag.parse(flagText);
        if (flag == null) {
            msg(sender, "flag.unknown",
                    "&cUnknown flag: {flag}. Available: explosion, fire, fluid, pvp, tp, mobs, trampling.",
                    "flag", flagText);
            return true;
        }
        Boolean value;
        if ("default".equalsIgnoreCase(valueText)
                || "inherit".equalsIgnoreCase(valueText)) {
            value = null;
        } else if ("allow".equalsIgnoreCase(valueText)
                || "on".equalsIgnoreCase(valueText)
                || "true".equalsIgnoreCase(valueText)) {
            value = Boolean.TRUE;
        } else if ("deny".equalsIgnoreCase(valueText)
                || "off".equalsIgnoreCase(valueText)
                || "false".equalsIgnoreCase(valueText)) {
            value = Boolean.FALSE;
        } else {
            msg(sender, "flag.invalid-state",
                    "&cState must be allow, deny, or default.");
            return true;
        }
        FlagChangeResult result =
                plugin.getLandManager().setLandFlag(actor, land, flag, value);
        if (!result.isSuccess()) {
            msg(sender, "flag.failed", "&c{reason}",
                    "reason", result.getError());
            return true;
        }
        msg(sender, "flag.changed",
                "&a{land}: {flag} is now {state} (source: {source}).",
                "land", result.getLand().getDisplayName(),
                "flag", flag.getId(),
                "state", result.isEffective() ? "allow" : "deny",
                "source", result.getSource());
        auditLand(sender, "SET_FLAG", null, result.getLand(),
                flag.getId() + "=" + (value == null ? "default"
                        : (result.isEffective() ? "allow" : "deny")));
        return true;
    }
}
