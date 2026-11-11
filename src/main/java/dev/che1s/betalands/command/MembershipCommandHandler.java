package dev.che1s.betalands.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.util.Text;

final class MembershipCommandHandler extends CommandSupport {
    MembershipCommandHandler(BetaLandsPlugin plugin) {
        super(plugin);
    }

    boolean handles(String subcommand) {
        return "add".equals(subcommand) || "kick".equals(subcommand)
                || "accept".equals(subcommand) || "deny".equals(subcommand)
                || "leave".equals(subcommand) || "perm".equals(subcommand);
    }

    boolean handle(String subcommand, CommandSender sender, String[] args,
                   boolean force) {
        if ("add".equals(subcommand) || "kick".equals(subcommand)) {
            return member(sender, subcommand, args, force);
        }
        if ("accept".equals(subcommand)) {
            return acceptInvitation(sender, args);
        }
        if ("deny".equals(subcommand)) {
            return denyInvitation(sender, args);
        }
        if ("leave".equals(subcommand)) {
            return leave(sender, args);
        }
        if ("perm".equals(subcommand)) {
            return permission(sender, args, force);
        }
        return false;
    }

    private boolean member(CommandSender sender, String action, String[] args,
                           boolean force) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        String required = "add".equals(action)
                ? PermissionNodes.MEMBER_INVITE
                : PermissionNodes.MEMBER_KICK;
        if (!force && !hasPermission(sender, required)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 2) {
            msg(sender, "member.usage-context",
                    "&eUsage: /land add|kick <player>");
            return true;
        }
        Land land = args.length >= 3 ? resolve(sender, args[2])
                : resolveManageableContextLand(actor);
        if (land == null) {
            sendContextLandError(sender, args.length >= 3 ? args[2] : null);
            return true;
        }
        if (requiresAdminCommand(plugin.getLandManager().isAdmin(actor),
                action, land.isOwner(actor.getName()))) {
            msg(sender, "member.admin-required",
                    "&cAdministrators must use /land admin-land add <player> <land> for another player's land.");
            return true;
        }
        return "add".equals(action)
                ? addMember(sender, land, args[1])
                : removeMember(sender, land, args[1], force);
    }

    static boolean requiresAdminCommand(boolean administrator, String action,
                                        boolean actingOnOwnLand) {
        return administrator && "add".equals(action) && !actingOnOwnLand;
    }

    private boolean addMember(CommandSender sender, Land land, String target) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!Text.validName(target, 2, 16)) {
            msg(sender, "common.invalid-player-name",
                    "&cPlayer names may only contain letters, numbers, underscores, and hyphens.");
            return true;
        }
        Player targetPlayer = findOnlinePlayer(target);
        if (targetPlayer == null) {
            msg(sender, "common.target-offline",
                    "&cTarget player is offline.");
            return true;
        }
        target = targetPlayer.getName();
        if (land.isOwner(target)
                || land.hasMember(target)) {
            msg(sender, "member.already-member",
                    "&c{player} is already a member of {land}.",
                    "player", target, "land", land.getDisplayName());
            return true;
        }
        if (!plugin.getLandManager().canChangeFamilyMembers(land)) {
            msg(sender, "member.debt",
                    "&cMember changes are disabled while the land family bank is in debt.");
            return true;
        }
        if (!plugin.getLandManager().canAddFamilyMember(land, target)) {
            msg(sender, "member.family-full",
                    "&cThis land family has reached its member limit ({limit}).",
                    "limit", Integer.toString(
                            plugin.getLandManager().getFamilyMemberLimit(land)));
            return true;
        }
        if (!plugin.getLandManager().canMeetJoinedLandLimit(land, target)) {
            LevelProfile profile = plugin.getLevels().getProfileForName(target);
            msg(sender, "member.joined-limit",
                    "&c{player} has reached the joined-land limit for their level ({limit}).",
                    "player", target,
                    "limit", Integer.toString(profile.getMaxJoinedLands()));
            return true;
        }
        plugin.getLandManager().getInvitations().create(
                actor.getName(), targetPlayer.getName(), land);
        String acceptCommand = "/land accept " + land.getDisplayName();
        String denyCommand = "/land deny " + land.getDisplayName();
        msg(targetPlayer, "member.invitation",
                "&e{inviter} invited you to join {land}. Current rent: {rent}/day. Accept with {accept}, or decline with {deny}. You may ignore this message.",
                "inviter", actor.getName(),
                "land", land.getDisplayName(),
                "rent", plugin.getEconomy().format(land.isRentEnabled()
                        ? land.getRentAmount() : 0.0D),
                "accept", acceptCommand,
                "deny", denyCommand);
        msg(sender, "member.invitation-sent",
                "&aInvitation sent to {player} for {land}.",
                "player", target, "land", land.getDisplayName());
        auditLand(sender, "INVITE_MEMBER", target, land,
                "Sent invitation; acceptance required");
        return true;
    }

    private boolean acceptInvitation(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 2) {
            msg(sender, "member.accept-usage",
                    "&eUsage: /land accept <land>");
            return true;
        }
        Land land = resolve(sender, args[1]);
        if (land == null) {
            sendContextLandError(sender, args[1]);
            return true;
        }
        dev.che1s.betalands.service.LandInvitationService.Invitation invitation =
                plugin.getLandManager().getInvitations().get(player.getName(), land);
        if (invitation == null) {
            msg(sender, "member.invitation-missing",
                    "&cNo pending invitation for {land}.",
                    "land", land.getDisplayName());
            return true;
        }
        if (!plugin.getLandManager().canChangeFamilyMembers(land)) {
            msg(sender, "member.debt",
                    "&cMember changes are disabled while the land family bank is in debt.");
            return true;
        }
        if (!plugin.getLandManager().canAddFamilyMember(land, player.getName())) {
            msg(sender, "member.family-full",
                    "&cThis land family has reached its member limit ({limit}).",
                    "limit", Integer.toString(
                            plugin.getLandManager().getFamilyMemberLimit(land)));
            return true;
        }
        if (!plugin.getLandManager()
                .canMeetJoinedLandLimit(land, player.getName())) {
            LevelProfile profile = plugin.getLevels()
                    .getProfileForName(player.getName());
            msg(sender, "member.joined-limit",
                    "&c{player} has reached the joined-land limit for their level ({limit}).",
                    "player", player.getName(),
                    "limit", Integer.toString(profile.getMaxJoinedLands()));
            return true;
        }
        if (!plugin.getLandManager().addMemberDirect(land, player.getName())) {
            msg(sender, "member.accept-failed",
                    "&cCould not join {land}.",
                    "land", land.getDisplayName());
            return true;
        }
        plugin.getLandManager().getInvitations().remove(player.getName(), land);
        msg(sender, "member.accepted",
                "&aYou joined {land} with build, break, use, container, and rent permissions.",
                "land", land.getDisplayName());
        Player inviter = findOnlinePlayer(invitation.getInviterName());
        if (inviter != null) {
            msg(inviter, "member.invitation-accepted",
                    "&a{player} accepted your invitation to join {land}.",
                    "player", player.getName(),
                    "land", land.getDisplayName());
        }
        auditLand(sender, "ACCEPT_MEMBER_INVITATION",
                invitation.getInviterName(), land,
                "Accepted invitation and added base permissions");
        refreshMaps();
        return true;
    }

    private boolean denyInvitation(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 2) {
            msg(sender, "member.deny-usage",
                    "&eUsage: /land deny <land>");
            return true;
        }
        Land land = resolve(sender, args[1]);
        if (land == null) {
            sendContextLandError(sender, args[1]);
            return true;
        }
        dev.che1s.betalands.service.LandInvitationService.Invitation invitation =
                plugin.getLandManager().getInvitations().remove(
                        player.getName(), land);
        if (invitation == null) {
            msg(sender, "member.invitation-missing",
                    "&cNo pending invitation for {land}.",
                    "land", land.getDisplayName());
            return true;
        }
        msg(sender, "member.invitation-denied",
                "&eYou declined the invitation to join {land}.",
                "land", land.getDisplayName());
        Player inviter = findOnlinePlayer(invitation.getInviterName());
        if (inviter != null) {
            msg(inviter, "member.invitation-declined",
                    "&e{player} declined your invitation to join {land}.",
                    "player", player.getName(),
                    "land", land.getDisplayName());
        }
        return true;
    }

    private boolean removeMember(CommandSender sender, Land land, String target,
                                 boolean force) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!force && !plugin.getLandManager().canChangeFamilyMembers(land)) {
            msg(sender, "member.debt",
                    "&cMember changes are disabled while the land family bank is in debt.");
            return true;
        }
        boolean removed = force
                ? plugin.getLandManager().forceRemoveMember(
                actor, land, target)
                : plugin.getLandManager().removeMember(
                actor, land, target);
        if (!removed) {
            msg(sender, "member.kick-failed",
                    "&cCould not remove player: you cannot manage this land or the target is the owner.");
            return true;
        }
        msg(sender, "member.kicked",
                "&aRemoved {player} from {land} and revoked that land's permissions.",
                "player", target, "land", land.getDisplayName());
        auditLand(sender, "REMOVE_MEMBER", target, land,
                "Removed member and revoked permissions");
        refreshMaps();
        return true;
    }

    private boolean permission(CommandSender sender, String[] args,
                               boolean force) {
        Player actor = requirePlayer(sender);
        if (actor == null) {
            return true;
        }
        if (!force && !hasPermission(sender,
                PermissionNodes.MEMBER_PERMISSION)) {
            noPermission(sender);
            return true;
        }
        if (args.length < 4 || args.length > 5) {
            msg(sender, "permission.usage-v2",
                    "&eUsage: /land perm <player> <permission|all> <allow|deny> [land]");
            return true;
        }
        Land land = args.length == 4
                ? resolveManageableContextLand(actor) : resolve(sender, args[4]);
        String target = args[1];
        String permissionText = args[2];
        String valueText = args[3];
        if (land == null) {
            sendContextLandError(sender, args.length == 5 ? args[4] : null);
            return true;
        }
        boolean allow = "allow".equalsIgnoreCase(valueText)
                || "true".equalsIgnoreCase(valueText)
                || "yes".equalsIgnoreCase(valueText);
        boolean handled;
        if ("all".equalsIgnoreCase(permissionText)) {
            java.util.Set<LandPermission> values =
                    allow ? LandPermission.allGrantable(
                            plugin.getSettings().isPvpAllowedByDefault())
                            : java.util.EnumSet.noneOf(LandPermission.class);
            handled = force
                    ? plugin.getLandManager().forceSetPermissionSet(
                    actor, land, target, values)
                    : plugin.getLandManager().setPermissionSet(
                    actor, land, target, values);
        } else {
            LandPermission permission = LandPermission.parse(permissionText);
            if (permission == null) {
                msg(sender, "permission.invalid",
                        "&cPermission must be build, break, use, container, rent, pvp, manage, or all.");
                return true;
            }
            if (!force && allow && permission == LandPermission.BUILD
                    && !plugin.getLandManager().hasPermissionInLand(
                    target, land, LandPermission.BUILD,
                    new java.util.HashSet<String>())
                    && !plugin.getLandManager()
                    .canMeetJoinedLandLimit(land, target)) {
                LevelProfile profile = plugin.getLevels()
                        .getProfileForName(target);
                msg(sender, "member.joined-limit",
                        "&c{player} has reached the joined-land limit for their level ({limit}).",
                        "player", target,
                        "limit", Integer.toString(
                                profile.getMaxJoinedLands()));
                return true;
            }
            handled = force
                    ? plugin.getLandManager().forceSetPermission(
                    actor, land, target, permission, allow)
                    : plugin.getLandManager().setPermission(
                    actor, land, target, permission, allow);
        }
        if (!handled) {
            msg(sender, "permission.failed",
                    "&cPermission update failed: the land is in debt, you cannot manage it, or the target is the owner.");
            return true;
        }
        msg(sender, "permission.changed",
                "&aSet {player}'s {permission} permission in {land} to {state}.",
                "player", target, "permission", permissionText,
                "land", land.getDisplayName(), "state", allow ? "allow" : "deny");
        auditLand(sender, "SET_PERMISSION", target, land,
                "Permission " + permissionText + "="
                        + (allow ? "allow" : "deny"));
        refreshMaps();
        return true;
    }

    private boolean leave(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }
        Land land = args.length >= 2
                ? resolve(sender, args[1])
                : plugin.getLandManager().findLandAt(player.getLocation());
        if (land == null) {
            List<Land> joined = new ArrayList<Land>();
            for (Land accessible
                    : plugin.getLandManager().getAccessibleLands(player.getName())) {
                if (!accessible.isOwner(player.getName())) {
                    joined.add(accessible);
                }
            }
            land = joined.size() == 1 ? joined.get(0) : null;
        }
        if (land == null || !plugin.getLandManager().leaveLand(player, land)) {
            msg(sender, "member.leave-failed-context",
                    "&cCould not leave: stand inside a joined land or specify the land name.");
            return true;
        }
        msg(sender, "member.left",
                "&aYou left {land} and lost that land's permissions.",
                "land", land.getDisplayName());
        auditLand(sender, "LEAVE_LAND", player.getName(), land, "Left land");
        return true;
    }
}
