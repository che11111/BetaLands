package dev.che1s.betalands.service;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.economy.EconomyService;
import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.HomePoint;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandIdentity;
import dev.che1s.betalands.model.Rect2i;

/**
 * Creates, expands and removes land geometry after quota and payment checks.
 */
public final class ClaimService {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final PermissionService permissions;
    private final LandOwnershipService ownership;

    public ClaimService(BetaLandsPlugin plugin, LandManager lands,
                        PermissionService permissions) {
        this.plugin = plugin;
        this.lands = lands;
        this.permissions = permissions;
        this.ownership = new LandOwnershipService(plugin, lands, permissions);
    }

    public CreateResult createMain(Player owner, String name) {
        return createMain(owner, name, false);
    }

    public CreateResult createMain(Player owner, String name,
                                               boolean force) {
        if (name != null && name.indexOf('.') >= 0) {
            return CreateResult.error("Main land names cannot contain a dot.");
        }
        if (name == null || !dev.che1s.betalands.util.Text.validName(name,
                plugin.getSettings().getMinNameLength(),
                plugin.getSettings().getMaxNameLength())) {
            return CreateResult.error(
                    "Names may only contain letters, numbers, underscores, and hyphens, length "
                            + plugin.getSettings().getMinNameLength() + "-"
                            + plugin.getSettings().getMaxNameLength() + ".");
        }
        LevelProfile profile = plugin.getLevels().getProfile(owner);
        if (!force && lands.countMainLands(owner.getName())
                >= profile.getMaxMainLands()) {
            return CreateResult.error("Your " + profile.getDisplayName()
                    + " level allows only " + profile.getMaxMainLands() + " main lands.");
        }
        String worldName = owner.getWorld().getName();
        Rect2i area = ClaimGeometry.chunkBounds(owner.getLocation().getBlockX(),
                owner.getLocation().getBlockZ());
        if (!force && area.area() > plugin.getSettings().getMaxMainArea()) {
            return CreateResult.error("Main-land area exceeds the limit: "
                    + plugin.getSettings().getMaxMainArea() + ".");
        }
        if (!force && profile.getMaxMainChunks() < 1) {
            return CreateResult.error(
                    "Your level does not allow creating a main land.");
        }
        if (lands.findByName(name) != null) {
            return CreateResult.error("A land with that name already exists.");
        }
        Land overlapping = findOverlappingMain(worldName, area);
        if (overlapping != null) {
            return CreateResult.error(
                    "This chunk already belongs to " + overlapping.getDisplayName() + ".");
        }
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(area);
        boolean admin = permissions.isAdmin(owner);
        double charged = 0.0D;
        if (!force) {
            EconomyService.PaymentResult payment = plugin.getEconomy().charge(
                    owner, profile.getCreateMainPrice(), "create main land");
            if (!payment.isSuccess()) {
                return CreateResult.error(payment.getError());
            }
            charged = payment.getAmount();
        }
        Land land = new Land(LandIdentity.newId(), owner.getName(), name,
                worldName, null, areas, 0, 127, true, System.currentTimeMillis(),
                "manual", null);
        land.setLevelId(profile.getId());
        land.setRentAmount(plugin.getSettings().getDefaultMainRent());
        land.setMaintenanceExempt(admin
                && plugin.getSettings().shouldExemptAdminCreatedMaintenance());
        land.setHome(new HomePoint(owner.getLocation().getX(), owner.getLocation().getY(),
                owner.getLocation().getZ(), owner.getLocation().getYaw(),
                owner.getLocation().getPitch()));
        lands.addLoadedLand(land);
        return CreateResult.success(land, charged);
    }

    public CreateResult claimChunk(Player actor, Land land) {
        return claimChunk(actor, land, false);
    }

    public CreateResult claimChunk(Player actor, Land land,
                                               boolean force) {
        if (land == null || land.isSubclaim()) {
            return CreateResult.error("Only main lands can be expanded.");
        }
        if (!permissions.isAdmin(actor) && !land.isOwner(actor.getName())) {
            return CreateResult.error("Only the main-land owner can expand it.");
        }
        if (!force && lands.isFamilyInDebt(land)) {
            return CreateResult.error(
                    "The land bank is in debt. Deposit funds before expanding.");
        }
        if (!land.getWorld().equalsIgnoreCase(actor.getWorld().getName())) {
            return CreateResult.error(
                    "You are in a different world from the main land.");
        }
        int chunkX = actor.getLocation().getBlockX() >> 4;
        int chunkZ = actor.getLocation().getBlockZ() >> 4;
        Rect2i chunk = ClaimGeometry.chunkBounds(chunkX << 4, chunkZ << 4);
        if (lands.landContainsChunk(land, chunkX, chunkZ)) {
            return CreateResult.error(
                    "This chunk already belongs to " + land.getDisplayName() + ".");
        }
        if (!lands.isChunkAdjacentToLand(land, chunkX, chunkZ)) {
            return CreateResult.error(
                    "This chunk does not touch " + land.getDisplayName() + ".");
        }
        Land overlapping = findOverlappingMain(land.getWorld(), chunk);
        if (overlapping != null) {
            return CreateResult.error(
                    "This chunk already belongs to " + overlapping.getDisplayName() + ".");
        }
        if (!force && land.area() + chunk.area()
                > plugin.getSettings().getMaxMainArea()) {
            return CreateResult.error(
                    "Expansion would exceed the main-land area limit: "
                            + plugin.getSettings().getMaxMainArea() + ".");
        }
        LevelProfile profile = profileFor(actor, land);
        int currentChunks = lands.getOccupiedChunks(land);
        if (!force && exceedsMainChunkLimit(profile, currentChunks)) {
            return CreateResult.error("This level allows at most "
                    + profile.getMaxMainChunks() + " chunks per main land.");
        }
        double claimPrice = calculateClaimPrice(profile, currentChunks);
        double charged = 0.0D;
        if (!force) {
            EconomyService.PaymentResult payment = plugin.getEconomy().charge(
                    actor, claimPrice, "claim chunk");
            if (!payment.isSuccess()) {
                return CreateResult.error(payment.getError());
            }
            charged = payment.getAmount();
        }
        land.replaceAreas(ClaimGeometry.mergeChunks(land.getAreas(), chunk));
        lands.indexClaimedChunk(land, chunkX, chunkZ);
        return CreateResult.success(land, charged);
    }

    public String unclaimChunk(Player actor, Land land) {
        if (land == null || land.isSubclaim()) {
            return "Only main lands can remove chunks.";
        }
        if (!permissions.isAdmin(actor) && !land.isOwner(actor.getName())) {
            return "Only the main-land owner can remove chunks.";
        }
        if (!land.getWorld().equalsIgnoreCase(actor.getWorld().getName())) {
            return "You are in a different world from the main land.";
        }
        int chunkX = actor.getLocation().getBlockX() >> 4;
        int chunkZ = actor.getLocation().getBlockZ() >> 4;
        if (!lands.landContainsChunk(land, chunkX, chunkZ)) {
            return "You must stand in a chunk owned by this main land.";
        }
        Rect2i removed = ClaimGeometry.chunkBounds(chunkX << 4, chunkZ << 4);
        for (Land child : lands.getChildren(land.getId())) {
            for (Rect2i childArea : child.getAreas()) {
                if (childArea.intersects(removed)) {
                    return "This chunk contains a subclaim. Delete all subclaims "
                            + "in this chunk before using /land unclaim.";
                }
            }
        }
        if (lands.getOccupiedChunks(land) <= 1) {
            return "A main land must keep at least one chunk. Delete the land instead.";
        }
        land.replaceAreas(ClaimGeometry.subtractChunk(land.getAreas(), removed));
        HomePoint home = land.getHome();
        if (home != null && !land.contains(land.getWorld(),
                (int) Math.floor(home.getX()), (int) Math.floor(home.getY()),
                (int) Math.floor(home.getZ()))) {
            land.setHome(null);
        }
        lands.stopAutoClaim(actor);
        lands.reindexAfterMutation();
        return null;
    }

    public CreateResult createSubclaim(Player actor, Land parent,
                                                   String name) {
        return createSubclaim(actor, parent, name, false);
    }

    public CreateResult createSubclaim(Player actor, Land parent,
                                                   String name, boolean force) {
        if (parent == null || parent.isSubclaim()) {
            return CreateResult.error(
                    "3D subclaims can only be created inside a main land.");
        }
        if (!permissions.isAdmin(actor) && !parent.isOwner(actor.getName())) {
            return CreateResult.error(
                    "Only the main-land owner can create subclaims.");
        }
        if (name != null && name.indexOf('.') >= 0) {
            return CreateResult.error("Subclaim names cannot contain a dot.");
        }
        if (!force && lands.isFamilyInDebt(parent)) {
            return CreateResult.error(
                    "The land bank is in debt. Deposit funds before creating a subclaim.");
        }
        LevelProfile profile = profileFor(actor, parent);
        int childCount = lands.getChildren(parent.getId()).size();
        if (!force && childCount >= profile.getMaxSubclaimsPerMain()) {
            return CreateResult.error("Your " + profile.getDisplayName()
                    + " level allows only " + profile.getMaxSubclaimsPerMain()
                    + " subclaims per main land.");
        }
        if (name == null || !dev.che1s.betalands.util.Text.validName(name,
                plugin.getSettings().getMinNameLength(),
                plugin.getSettings().getMaxNameLength())) {
            return CreateResult.error("Invalid subclaim name.");
        }
        SelectionManager.Selection selection =
                plugin.getSelections().get(actor.getName());
        if (selection == null || !selection.isComplete()) {
            return CreateResult.error(
                    "Select POS1 and POS2 with a wooden hoe, or use /land pos1 and /land pos2.");
        }
        if (!selection.sameWorld()
                || !selection.getWorldName().equalsIgnoreCase(parent.getWorld())) {
            return CreateResult.error(
                    "The subclaim must be in the same world as its main land.");
        }
        int minX = Math.min(selection.getFirst().getBlockX(),
                selection.getSecond().getBlockX());
        int maxX = Math.max(selection.getFirst().getBlockX(),
                selection.getSecond().getBlockX());
        int minZ = Math.min(selection.getFirst().getBlockZ(),
                selection.getSecond().getBlockZ());
        int maxZ = Math.max(selection.getFirst().getBlockZ(),
                selection.getSecond().getBlockZ());
        Rect2i area = new Rect2i(minX, minZ, maxX, maxZ);
        if (!ClaimGeometry.isAreaInsideParent(parent, area)) {
            return CreateResult.error(
                    "The subclaim must be fully inside " + parent.getDisplayName() + ".");
        }
        int minY = Math.min(selection.getFirst().getBlockY(),
                selection.getSecond().getBlockY());
        int maxY = Math.max(selection.getFirst().getBlockY(),
                selection.getSecond().getBlockY());
        World world = Bukkit.getServer().getWorld(parent.getWorld());
        if (world != null) {
            minY = Math.max(0, minY);
            maxY = Math.min(world.getMaxHeight() - 1, maxY);
        }
        String territoryName = parent.getTerritoryName() + "." + name;
        if (lands.findByName(territoryName) != null) {
            return CreateResult.error(
                    "A subclaim with that name already exists in this main land.");
        }
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(area);
        Land subclaim = new Land(LandIdentity.newId(), parent.getOwner(), name,
                parent.getWorld(), parent.getId(), areas, minY, maxY,
                true, System.currentTimeMillis(), "manual", null);
        subclaim.setRentAmount(plugin.getSettings().getDefaultSubclaimRent());
        subclaim.setHome(new HomePoint(actor.getLocation().getX(),
                actor.getLocation().getY(), actor.getLocation().getZ(),
                actor.getLocation().getYaw(), actor.getLocation().getPitch()));
        if (!force && subclaim.volume()
                > plugin.getSettings().getMaxSubVolume()) {
            return CreateResult.error("Subclaim volume exceeds the limit: "
                    + plugin.getSettings().getMaxSubVolume() + ".");
        }
        for (Land sibling : lands.getChildren(parent.getId())) {
            if (subclaim.getMinY() > sibling.getMaxY()
                    || subclaim.getMaxY() < sibling.getMinY()) {
                continue;
            }
            for (Rect2i existing : sibling.getAreas()) {
                if (area.intersects(existing)) {
                    return CreateResult.error(
                            "The subclaim overlaps " + sibling.getDisplayName()
                                    + " at the same height.");
                }
            }
        }
        double price = EconomyService.round(
                subclaim.volume() * profile.getSubclaimPerBlockPrice()).doubleValue();
        double charged = 0.0D;
        if (!force) {
            EconomyService.PaymentResult payment = plugin.getEconomy().charge(
                    actor, price, "create subclaim");
            if (!payment.isSuccess()) {
                return CreateResult.error(payment.getError());
            }
            charged = payment.getAmount();
        }
        lands.addLoadedLand(subclaim);
        return CreateResult.success(subclaim, charged);
    }

    public List<Land> getClaimCandidates(Player player) {
        List<Land> candidates = new ArrayList<Land>();
        Land current = lands.findMainAtChunk(player.getWorld().getName(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
        if (current != null && current.isOwner(player.getName())) {
            candidates.add(current);
        }
        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        for (Land land : lands.getOwnedMainLands(player.getName())) {
            if (land.getWorld().equalsIgnoreCase(player.getWorld().getName())
                    && lands.isChunkAdjacentToLand(land, chunkX, chunkZ)
                    && !candidates.contains(land)) {
                candidates.add(land);
            }
        }
        return candidates;
    }

    public Land getOwnedParentAtPlayer(Player player) {
        Land current = lands.findLandAt(player.getLocation());
        if (current == null) {
            return null;
        }
        Land main = current.isSubclaim()
                ? lands.getById(current.getParentId()) : current;
        return main != null && (permissions.isAdmin(player)
                || main.isOwner(player.getName())) ? main : null;
    }

    public String renameLand(Player actor, Land land, String newName) {
        return ownership.renameLand(actor, land, newName);
    }

    public String transferHighestOwner(Player actor, Land land, Player target) {
        return ownership.transferHighestOwner(actor, land, target);
    }

    public boolean deleteLand(Player actor, Land land) {
        return ownership.deleteLand(actor, land);
    }

    public boolean deleteLand(Player actor, Land land, boolean force) {
        return ownership.deleteLand(actor, land, force);
    }

    public static boolean hasZeroBankBalance(double balance) {
        return LandOwnershipService.hasZeroBankBalance(balance);
    }

    void deleteLandCascade(Land land) {
        ownership.deleteLandCascade(land);
    }

    private LevelProfile profileFor(Player actor, Land land) {
        if (land.isOwner(actor.getName())) {
            LevelProfile current = plugin.getLevels().getProfile(actor);
            land.setLevelId(current.getId());
            if (!land.isSubclaim()) {
                for (Land child : lands.getChildren(land.getId())) {
                    child.setLevelId(current.getId());
                }
            }
            return current;
        }
        return plugin.getLevels().getProfile(land);
    }

    private Land findOverlappingMain(String worldName, Rect2i area) {
        for (Land existing : lands.getLands()) {
            if (existing.isSubclaim()
                    || !existing.getWorld().equalsIgnoreCase(worldName)) {
                continue;
            }
            for (Rect2i other : existing.getAreas()) {
                if (area.intersects(other)) {
                    return existing;
                }
            }
        }
        return null;
    }

    public static Rect2i chunkBounds(int blockX, int blockZ) {
        return ClaimGeometry.chunkBounds(blockX, blockZ);
    }

    public static int countOccupiedChunks(List<Rect2i> areas) {
        return ClaimGeometry.countOccupiedChunks(areas);
    }

    public static double calculateClaimPrice(LevelProfile profile, long chunks) {
        return ClaimGeometry.calculateClaimPrice(profile, chunks);
    }

    public static boolean exceedsMainChunkLimit(LevelProfile profile,
                                                int currentChunks) {
        return ClaimGeometry.exceedsMainChunkLimit(profile, currentChunks);
    }

    static List<Rect2i> subtractChunk(List<Rect2i> areas, Rect2i removed) {
        return ClaimGeometry.subtractChunk(areas, removed);
    }
}
