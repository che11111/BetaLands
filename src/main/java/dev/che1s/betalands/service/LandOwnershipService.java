package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.economy.EconomyService;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.util.Text;

/**
 * Owns rename, ownership transfer and soft-delete semantics.
 */
public final class LandOwnershipService {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final PermissionService permissions;

    public LandOwnershipService(BetaLandsPlugin plugin, LandManager lands,
                                PermissionService permissions) {
        this.plugin = plugin;
        this.lands = lands;
        this.permissions = permissions;
    }

    public String renameLand(Player actor, Land land, String newName) {
        if (land == null || actor == null) {
            return "Land not found.";
        }
        Land main = land.isSubclaim() ? lands.getById(land.getParentId()) : land;
        if (!permissions.isAdmin(actor) && !land.isOwner(actor.getName())
                && (main == null || !main.isOwner(actor.getName()))) {
            return "Only the land owner or the main-land owner can rename it.";
        }
        if (newName == null) {
            return "Invalid land name.";
        }
        String cleaned = newName.trim();
        if (cleaned.indexOf('.') >= 0) {
            return "Land names cannot contain a dot.";
        }
        if (!Text.validName(cleaned, plugin.getSettings().getMinNameLength(),
                plugin.getSettings().getMaxNameLength())) {
            return "Names may only contain letters, numbers, underscores, and hyphens, length "
                    + plugin.getSettings().getMinNameLength() + "-"
                    + plugin.getSettings().getMaxNameLength() + ".";
        }
        String candidate = land.isSubclaim()
                ? (main == null ? "" : main.getTerritoryName() + ".") + cleaned
                : cleaned;
        Land existing = lands.findByName(candidate);
        if (existing != null && existing != land) {
            return "A land with that name already exists.";
        }
        if (land.getName().equals(cleaned)) {
            return "The land already has that name.";
        }
        land.rename(cleaned);
        lands.reindexAfterMutation();
        return null;
    }

    public String transferHighestOwner(Player actor, Land land, Player target) {
        if (land == null || target == null) {
            return "Land or target player not found.";
        }
        if (land.isSubclaim()) {
            return "Only a main land can transfer its highest owner.";
        }
        if (actor == null || (!permissions.isAdmin(actor)
                && !land.isOwner(actor.getName()))) {
            return "Only the current main-land owner or an admin can transfer ownership.";
        }
        if (land.isOwner(target.getName())) {
            return "That player is already the highest owner.";
        }
        LevelProfile targetProfile = plugin.getLevels().getProfile(target);
        if (lands.countMainLands(target.getName())
                >= targetProfile.getMaxMainLands()) {
            return "The target player's " + targetProfile.getDisplayName()
                    + " level allows only " + targetProfile.getMaxMainLands()
                    + " main lands.";
        }

        String previousOwner = land.getOwner();
        land.setOwner(target.getName());
        land.removeMember(target.getName());
        land.setLevelId(targetProfile.getId());
        for (Land child : lands.getChildren(land.getId())) {
            if (child.isOwner(previousOwner)) {
                child.setOwner(target.getName());
                child.removeMember(target.getName());
                child.setLevelId(targetProfile.getId());
            }
        }
        lands.reindexAfterMutation();
        return null;
    }

    public boolean deleteLand(Player actor, Land land) {
        return deleteLand(actor, land, false);
    }

    public boolean deleteLand(Player actor, Land land, boolean force) {
        if (land == null || !(permissions.isAdmin(actor)
                || land.isOwner(actor.getName()))) {
            return false;
        }
        if (!force && plugin != null
                && plugin.getMaintenanceService() != null) {
            Land main = plugin.getMaintenanceService().getBankLand(land);
            if (main != null && !hasZeroBankBalance(main.getBankBalance())) {
                return false;
            }
        }
        softDeleteLandCascade(land, actor.getName());
        return true;
    }

    public static boolean hasZeroBankBalance(double balance) {
        return EconomyService.round(balance).doubleValue() == 0.0D;
    }

    void deleteLandCascade(Land land) {
        softDeleteLandCascade(land, "SYSTEM");
    }

    private void softDeleteLandCascade(Land land, String actor) {
        List<Land> related = new ArrayList<Land>();
        related.add(land);
        if (!land.isSubclaim()) {
            related.addAll(lands.getChildren(land.getId()));
        }
        long deletedAt = System.currentTimeMillis();
        for (Land remove : related) {
            remove.markDeleted(actor, deletedAt);
            lands.archiveLand(remove);
            lands.getInvitations().removeForLand(remove.getId());
        }
        Set<String> removedIds = new HashSet<String>();
        for (Land remove : related) {
            removedIds.add(remove.getId());
        }
        for (RentAccount account : lands.getRentAccounts().values()) {
            if (removedIds.contains(account.getDebtLandId())) {
                account.setDebtLandId(null);
            }
        }
        lands.reindexAfterMutation();
    }
}
