package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.service.ClaimResult;
import dev.che1s.betalands.service.CreateResult;
import dev.che1s.betalands.service.LandManager;

final class ClaimCommandHandler extends CommandSupport {
    ClaimCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "pos1".equals(subcommand) || "pos2".equals(subcommand)
                || "create".equals(subcommand) || "claim".equals(subcommand)
                || "unclaim".equals(subcommand)
                || "subcreate".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args,
                   boolean force) {
        if ("pos1".equals(subcommand) || "pos2".equals(subcommand)) {
            return setPosition(sender, subcommand);
        }
        if ("create".equals(subcommand)) {
            return create(sender, args, force);
        }
        if ("claim".equals(subcommand)) {
            return claim(sender, args, force);
        }
        if ("unclaim".equals(subcommand)) {
            return unclaim(sender, args);
        }
        if ("subcreate".equals(subcommand)) {
            return createSubclaim(sender, args, force);
        }
        return false;
    }

    private boolean setPosition(CommandSender sender, String which) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.CREATE_SUBCLAIM)) {
            noPermission(sender);
            return true;
        }
        Location location = player.getLocation();
        if ("pos1".equals(which)) {
            plugin.getSelections().setFirst(player.getName(), location);
        } else {
            plugin.getSelections().setSecond(player.getName(), location);
        }
        msg(player, "selection.set", "&a{pos} set to {x}, {y}, {z}.",
                "pos", which.toUpperCase(),
                "x", Integer.toString(location.getBlockX()),
                "y", Integer.toString(location.getBlockY()),
                "z", Integer.toString(location.getBlockZ()));
        return true;
    }

    private boolean create(CommandSender sender, String[] args,
                           boolean force) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.CREATE_MAIN)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "create.usage", "&eUsage: /land create <name>");
            return true;
        }
        CreateResult result =
                plugin.getLandManager().createMain(player, args[1], force);
        if (!result.isSuccess()) {
            msg(sender, "create.failed", "&c{reason}",
                    "reason", result.getError());
            return true;
        }
        msg(sender, "create.success",
                "&aClaimed the current 16x16 chunk and created main land {land}{cost}.",
                "land", result.getLand().getDisplayName(),
                "cost", costSuffix(result.getPrice()));
        auditLand(sender, "CREATE_MAIN", null, result.getLand(),
                "Created main land " + result.getLand().getDisplayName());
        auditEconomy(sender, "CREATE_MAIN_FEE", result.getLand(),
                result.getPrice(), result.getLand().getBankBalance(),
                "Main land creation fee");
        refreshMaps();
        return true;
    }

    private boolean claim(CommandSender sender, String[] args,
                          boolean force) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.CREATE_CLAIM)) {
            noPermission(sender);
            return true;
        }
        boolean auto = args.length >= 2 && "auto".equalsIgnoreCase(args[1]);
        if (!auto) {
            Land target = args.length >= 2
                    ? resolve(sender, args[1])
                    : resolveSingleClaimTarget(sender, player);
            if (target == null) {
                return true;
            }
            CreateResult result =
                    plugin.getLandManager().claimChunk(player, target, force);
            if (!result.isSuccess()) {
                msg(sender, "claim.failed", "&c{reason}",
                        "reason", result.getError());
                return true;
            }
            msg(sender, "claim.success",
                    "&aClaimed the current chunk for {land}. Area={area}{cost}.",
                    "land", target.getDisplayName(),
                    "area", Long.toString(target.area()),
                    "cost", costSuffix(result.getPrice()));
            auditLand(sender, "CLAIM_CHUNK", null, target,
                    "Claimed one 16x16 chunk");
            auditEconomy(sender, "CLAIM_CHUNK_FEE", target,
                    result.getPrice(), target.getBankBalance(),
                    "Chunk claim fee");
            refreshMaps();
            return true;
        }
        if (plugin.getLandManager().isAutoClaimEnabled(player)) {
            Land target = plugin.getLandManager().getAutoClaimTarget(player);
            plugin.getLandManager().stopAutoClaim(player);
            msg(sender, "claim.auto-off", "&eAuto-claim is now off{land}.",
                    "land", target == null ? "."
                            : " (" + target.getDisplayName() + ").");
            if (target != null) {
                auditLand(sender, "AUTO_CLAIM_OFF", null, target,
                        "Auto-claim disabled");
            }
            return true;
        }
        Land target = args.length >= 3
                ? resolve(sender, args[2])
                : resolveSingleClaimTarget(sender, player);
        if (target == null) {
            return true;
        }
        if (!plugin.getLandManager().isAdmin(player)
                && !target.isOwner(player.getName())) {
            msg(sender, "claim.auto-owner",
                    "&cOnly the main-land owner can enable auto-claim.");
            return true;
        }
        if (target.isSubclaim()) {
            msg(sender, "claim.auto-main-only",
                    "&cAuto-claim only works for main lands.");
            return true;
        }
        plugin.getLandManager().startAutoClaim(player, target, force);
        ClaimResult initial =
                plugin.getLandManager().tryAutoClaim(player, force);
        if (initial.getState() == ClaimResult.State.CLAIMED) {
            msg(sender, "claim.auto-claimed",
                    "&aAuto-claim is active and claimed the current chunk for {land}{cost}.",
                    "land", target.getDisplayName(),
                    "cost", costSuffix(initial.getPrice()));
            auditLand(sender, "CLAIM_CHUNK_AUTO", null, target,
                    "Auto-claimed current chunk");
            auditEconomy(sender, "CLAIM_CHUNK_FEE_AUTO", target,
                    initial.getPrice(), target.getBankBalance(),
                    "Auto-claim chunk fee");
            refreshMaps();
        } else if (initial.getState() == ClaimResult.State.FAILED) {
            plugin.getLandManager().stopAutoClaim(player);
            msg(sender, "claim.auto-failed", "&c{reason}",
                    "reason", initial.getError());
            return true;
        } else {
            LevelProfile profile = plugin.getLevels().getProfile(target);
            double nextPrice = LandManager.calculateClaimPrice(
                    profile, plugin.getLandManager().getOccupiedChunks(target));
            msg(sender, "claim.auto-on-scaled",
                    "&aAuto-claim enabled for {land}. Next chunk={price}; base={base}, increment={increment} per existing chunk. Use /land claim auto again to stop.",
                    "land", target.getDisplayName(),
                    "price", plugin.getEconomy().format(nextPrice),
                    "base", plugin.getEconomy().format(
                            profile.getClaimChunkPrice()),
                    "increment", plugin.getEconomy().format(
                            profile.getClaimChunkIncrement()));
        }
        auditLand(sender, "AUTO_CLAIM_ON", null, target, "Auto-claim enabled");
        return true;
    }

    private Land resolveSingleClaimTarget(CommandSender sender, Player player) {
        List<Land> candidates = plugin.getLandManager().getClaimCandidates(player);
        if (candidates.isEmpty()) {
            msg(sender, "claim.no-target",
                    "&cStand inside your own main land or in an unclaimed chunk touching it.");
            return null;
        }
        if (candidates.size() > 1) {
            List<String> names = new ArrayList<String>();
            for (Land candidate : candidates) {
                names.add(candidate.getName());
            }
            msg(sender, "claim.multiple-targets",
                    "&cThis chunk touches multiple lands ({lands}). Use /land claim <land>.",
                    "lands", String.join(", ", names));
            return null;
        }
        return candidates.get(0);
    }

    private boolean unclaim(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Land target;
        if (args.length >= 2) {
            target = resolve(sender, args[1]);
            if (target != null && target.isSubclaim()) {
                target = plugin.getLandManager().getById(target.getParentId());
            }
        } else {
            target = plugin.getLandManager().getOwnedParentAtPlayer(player);
        }
        if (target == null) {
            msg(sender, "unclaim.not-found",
                    "&cNo removable main land found here.");
            return true;
        }
        String error = plugin.getLandManager().unclaimChunk(player, target);
        if (error != null) {
            msg(sender, "unclaim.failed", "&cUnclaim failed: {reason}",
                    "reason", error);
            return true;
        }
        msg(sender, "unclaim.success",
                "&aRemoved the current chunk from {land}. Remaining chunks={chunks}.",
                "land", target.getDisplayName(),
                "chunks", Integer.toString(
                        plugin.getLandManager().getOccupiedChunks(target)));
        auditLand(sender, "UNCLAIM_CHUNK", null, target,
                "Removed one 16x16 chunk");
        refreshMaps();
        return true;
    }

    private boolean createSubclaim(CommandSender sender, String[] args,
                                   boolean force) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!hasPermission(sender, PermissionNodes.CREATE_SUBCLAIM)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "subclaim.usage", "&eUsage: /land subcreate <name>");
            return true;
        }
        Land parent = plugin.getLandManager().getOwnedParentAtPlayer(player);
        if (parent == null) {
            msg(sender, "subclaim.inside-owner-land",
                    "&cStand inside a main land that you own.");
            return true;
        }
        CreateResult result =
                plugin.getLandManager().createSubclaim(
                        player, parent, args[1], force);
        if (!result.isSuccess()) {
            msg(sender, "subclaim.failed", "&c{reason}",
                    "reason", result.getError());
            return true;
        }
        plugin.getSelections().clear(player.getName());
        msg(sender, "subclaim.success",
                "&aCreated 3D subclaim {land}, Y={minY}..{maxY}{cost}.",
                "land", result.getLand().getDisplayName(),
                "minY", Integer.toString(result.getLand().getMinY()),
                "maxY", Integer.toString(result.getLand().getMaxY()),
                "cost", costSuffix(result.getPrice()));
        auditLand(sender, "CREATE_SUBCLAIM", null, result.getLand(),
                "Created subclaim " + result.getLand().getDisplayName());
        auditEconomy(sender, "CREATE_SUBCLAIM_FEE", result.getLand(),
                result.getPrice(), result.getLand().getBankBalance(),
                "Subclaim volume " + result.getLand().volume() + " blocks");
        refreshMaps();
        return true;
    }
}
