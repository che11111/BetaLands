package dev.che1s.betalands.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import dev.che1s.betalands.levels.LevelProfile;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.model.Rect2i;

public class LandManagerChunkTest {
    @Test
    public void computesNegativeChunkBounds() {
        Rect2i area = LandManager.chunkBounds(-1, -17);
        assertEquals(-16, area.getMinX());
        assertEquals(-1, area.getMaxX());
        assertEquals(-32, area.getMinZ());
        assertEquals(-17, area.getMaxZ());
    }

    @Test
    public void requiresSharedChunkEdgeForExpansion() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        Land land = new Land("alice:aaa", "Alice", "AAA", "world", null,
                areas, 0, 127, true, 0L, "manual", null);
        LandManager manager = new LandManager(null);
        assertTrue(manager.isChunkAdjacentToLand(land, 1, 0));
        assertTrue(manager.isChunkAdjacentToLand(land, 0, -1));
        assertFalse(manager.isChunkAdjacentToLand(land, 1, 1));
        assertFalse(manager.isChunkAdjacentToLand(land, 2, 0));
    }

    @Test
    public void resolvesOwnedDotNotationForSubclaim() {
        List<Rect2i> mainAreas = new ArrayList<Rect2i>();
        mainAreas.add(new Rect2i(0, 0, 31, 15));
        List<Rect2i> subAreas = new ArrayList<Rect2i>();
        subAreas.add(new Rect2i(0, 0, 15, 15));
        Land main = new Land("alice:aaa", "Alice", "AAA", "world", null,
                mainAreas, 0, 127, true, 0L, "manual", null);
        Land sub = new Land("alice:aaa/bb", "Alice", "bb", "world", main.getId(),
                subAreas, 10, 20, true, 0L, "manual", null);
        LandManager manager = new LandManager(null);
        manager.addLoadedLand(main);
        manager.addLoadedLand(sub);
        assertEquals(sub, manager.resolveRegion("AAA.bb", "Alice"));
        assertEquals(sub, manager.resolveRegion("Alice:AAA.bb", null));
    }

    @Test
    public void joinedLandsAreCountedByEffectiveBuildPermission() {
        List<Rect2i> mainAreas = new ArrayList<Rect2i>();
        mainAreas.add(new Rect2i(0, 0, 31, 15));
        List<Rect2i> subAreas = new ArrayList<Rect2i>();
        subAreas.add(new Rect2i(0, 0, 15, 15));
        Land main = new Land("alice:aaa", "Alice", "AAA", "world", null,
                mainAreas, 0, 127, true, 0L, "manual", null);
        Land sub = new Land("alice:aaa/bb", "Alice", "bb", "world", main.getId(),
                subAreas, 10, 20, true, 0L, "manual", null);
        LandManager manager = new LandManager(null);
        manager.addLoadedLand(main);
        manager.addLoadedLand(sub);

        main.setMemberPermissions("Builder", EnumSet.of(
                LandPermission.BUILD, LandPermission.BREAK,
                LandPermission.USE, LandPermission.CONTAINER));
        manager.rebuildIndexes();
        assertEquals(1, manager.getJoinedLandCount("Builder"));

        sub.setMemberPermissions("Builder", EnumSet.noneOf(LandPermission.class));
        manager.rebuildIndexes();
        assertEquals(1, manager.getJoinedLandCount("Builder"));

        sub.setMemberPermissions("Builder", EnumSet.of(LandPermission.BUILD));
        manager.rebuildIndexes();
        assertEquals(2, manager.getJoinedLandCount("Builder"));
    }

    @Test
    public void rentMembersArePlayersWithEffectiveBuildPermission() {
        Land main = land("main-rent", "Alice", "AAA", null);
        Land sub = land("sub-rent", "Alice", "shop", main.getId());
        LandManager manager = new LandManager(null);
        manager.addLoadedLand(main);
        manager.addLoadedLand(sub);
        main.setMemberPermissions("Builder",
                EnumSet.of(LandPermission.BUILD, LandPermission.RENT));

        assertTrue(manager.getBuildAccessMembers(sub).contains("builder"));
        assertFalse(manager.getBuildAccessMembers(sub).contains("alice"));
        assertTrue(manager.getRentPayers(sub).contains("builder"));

        sub.setMemberPermissions("Builder", EnumSet.noneOf(LandPermission.class));
        assertFalse(manager.getBuildAccessMembers(sub).contains("builder"));
    }

    @Test
    public void rentPermissionControlsWhoPaysRent() {
        Land main = land("rent-permission", "Alice", "AAA", null);
        LandManager manager = new LandManager(null);
        manager.addLoadedLand(main);
        main.setMemberPermissions("Builder",
                EnumSet.of(LandPermission.BUILD));

        assertFalse(manager.getRentPayers(main).contains("builder"));
        main.setMemberPermission("Builder", LandPermission.RENT, true);
        assertTrue(manager.getRentPayers(main).contains("builder"));
        assertFalse(manager.getRentPayers(main).contains("alice"));
    }

    @Test
    public void eachPlayerHasOneGlobalRentAccount() {
        LandManager manager = new LandManager(null);
        RentAccount first = manager.getOrCreateRentAccount("Builder");
        first.addBalance(20.0D);
        RentAccount second = manager.getOrCreateRentAccount("builder");
        assertTrue(first == second);
        assertEquals(20.0D, second.getBalance(), 0.0D);
    }

    @Test
    public void enforcesGloballyUniqueMainLandNames() {
        LandManager manager = new LandManager(null);
        manager.addLoadedLand(land("id-one", "Alice", "AAA", null));

        assertTrue(manager.isTerritoryNameAvailable("BBB"));
        assertEquals("AAA", manager.findByName("aaa").getTerritoryName());
        try {
            manager.addLoadedLand(land("id-two", "Bob", "aaa", null));
            throw new AssertionError("Expected duplicate global land name to be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Duplicate land name: aaa", expected.getMessage());
        }
    }

    @Test
    public void resolvesCurrentAndLegacyNamesWithoutOwnerPrefixDisplay() {
        LandManager manager = new LandManager(null);
        Land main = land("main-id", "Alice", "AAA", null);
        Land sub = land("sub-id", "Alice", "shop", main.getId());
        manager.addLoadedLand(main);
        manager.addLoadedLand(sub);

        assertEquals(main, manager.resolveRegion("AAA", null));
        assertEquals(sub, manager.resolveRegion("AAA.shop", null));
        assertEquals(sub, manager.resolveRegion("Alice:AAA.shop", null));
        assertEquals("AAA", main.getDisplayName());
        assertEquals("AAA.shop", sub.getDisplayName());
        assertNull(manager.findByName("shop"));
    }

    @Test
    public void listsFamilyMembersWithoutDuplicateMainOwner() {
        LandManager manager = new LandManager(null);
        Land main = land("main-id", "Alice", "AAA", null);
        Land sub = land("sub-id", "Bob", "shop", main.getId());
        main.setMemberPermissions("Charlie", EnumSet.of(LandPermission.BUILD));
        sub.setMemberPermissions("Bob", EnumSet.of(LandPermission.BUILD));
        manager.addLoadedLand(main);
        manager.addLoadedLand(sub);

        assertEquals(java.util.Arrays.asList("bob", "charlie"),
                manager.getFamilyMemberNames(main));
    }

    @Test
    public void deletingMainLandCascadesToSubclaims() {
        LandManager manager = new LandManager(null);
        Land main = land("main-delete", "Alice", "AAA", null);
        Land first = land("sub-delete-one", "Alice", "one", main.getId());
        Land second = land("sub-delete-two", "Alice", "two", main.getId());
        manager.addLoadedLand(main);
        manager.addLoadedLand(first);
        manager.addLoadedLand(second);

        ClaimService claims = new ClaimService(
                null, manager, new PermissionService(null, manager));
        claims.deleteLandCascade(main);

        assertNull(manager.getById(main.getId()));
        assertNull(manager.getById(first.getId()));
        assertNull(manager.getById(second.getId()));
        assertTrue(manager.getChildren(main.getId()).isEmpty());
        assertEquals(0, manager.countMainLands("Alice"));
    }

    @Test
    public void forcedMemberAddBypassesFamilyQuota() {
        LandManager manager = new LandManager(null);
        Land main = land("force-main", "Alice", "AAA", null);
        manager.addLoadedLand(main);
        PermissionService permissions =
                new PermissionService(null, manager);
        MembershipService membership = new MembershipService(
                null, manager, permissions,
                new AccessPolicy(null, manager, permissions));

        for (int index = 0; index < 20; index++) {
            assertTrue(membership.forceAddMemberDirect(
                    main, "Builder" + index));
        }

        assertEquals(20, main.getMembers().size());
    }

    @Test
    public void landDeletionRequiresRoundedZeroBankBalance() {
        assertTrue(ClaimService.hasZeroBankBalance(0.0D));
        assertTrue(ClaimService.hasZeroBankBalance(-0.001D));
        assertFalse(ClaimService.hasZeroBankBalance(0.01D));
        assertFalse(ClaimService.hasZeroBankBalance(-0.01D));
    }

    @Test
    public void forceDeleteIgnoresNonZeroBankBalance() {
        LandManager manager = new LandManager(null);
        Land main = land("force-delete", "Alice", "AAA", null);
        main.setBankBalance(25.0D);
        manager.addLoadedLand(main);
        ClaimService claims = new ClaimService(
                null, manager, new PermissionService(null, manager));

        assertTrue(claims.deleteLand(admin(), main, true));
        assertNull(manager.getById(main.getId()));
    }

    @Test
    public void softDeleteRetainsHistoryAndAllowsNameReuse() {
        LandManager manager = new LandManager(null);
        Land original = land("old-id", "Alice", "AAA", null);
        manager.addLoadedLand(original);
        ClaimService claims = new ClaimService(
                null, manager, new PermissionService(null, manager));

        assertTrue(claims.deleteLand(admin(), original, true));
        assertNull(manager.getById("old-id"));
        assertNull(manager.findByName("AAA"));
        Land archived = manager.getByIdIncludingDeleted("old-id");
        assertNotNull(archived);
        assertTrue(archived.isDeleted());
        assertEquals("Admin", archived.getDeletedBy());
        assertTrue(archived.getDeletedAt() > 0L);

        Land replacement = land("new-id", "Bob", "AAA", null);
        manager.addLoadedLand(replacement);
        assertEquals("new-id", manager.findByName("AAA").getId());
        assertEquals(1, manager.getDeletedLands().size());
    }

    @Test
    public void forceSetHomeAllowsLocationOutsideLandInSameWorld() {
        LandManager manager = new LandManager(null);
        Land main = land("home", "Alice", "AAA", null);
        manager.addLoadedLand(main);

        assertFalse(manager.setHome(admin(), main,
                new Location(world("world"), 100, 64, 100), true));
        assertTrue(manager.setHome(admin(), main,
                new Location(world("world"), 100, 64, 100), false));
        assertEquals(100.0D, main.getHome().getX(), 0.0D);
    }

    @Test
    public void calculatesTieredExpansionPriceFromCurrentChunkCount() {
        LevelProfile defaultProfile = profile("default", 5.0D);
        LevelProfile vip = profile("vip", 4.0D);
        LevelProfile svip = profile("svip", 3.0D);
        assertEquals(45.0D, LandManager.calculateClaimPrice(defaultProfile, 5L), 0.0D);
        assertEquals(40.0D, LandManager.calculateClaimPrice(vip, 5L), 0.0D);
        assertEquals(35.0D, LandManager.calculateClaimPrice(svip, 5L), 0.0D);
        assertEquals(25.0D, LandManager.calculateClaimPrice(defaultProfile, 1L), 0.0D);
    }

    @Test
    public void enforcesPerLevelMainChunkLimit() {
        LevelProfile profile = profile("default", 5.0D);
        assertFalse(LandManager.exceedsMainChunkLimit(profile, 99));
        assertTrue(LandManager.exceedsMainChunkLimit(profile, 100));
    }

    @Test
    public void removesOneChunkWithoutChangingRemainingCoverage() {
        List<Rect2i> remaining = LandManager.subtractChunk(
                java.util.Collections.singletonList(new Rect2i(0, 0, 31, 31)),
                new Rect2i(0, 0, 15, 15));
        assertEquals(3, LandManager.countOccupiedChunks(remaining));
        assertEquals(768L, area(remaining));
    }

    private LevelProfile profile(String id, double increment) {
        return new LevelProfile(id, id, "", 0, 3, 100, 3, 8, 5,
                300.0D, 20.0D, increment, 0.01D, 10.0D, 2.0D);
    }

    private long area(List<Rect2i> areas) {
        long result = 0L;
        for (Rect2i area : areas) {
            result += area.area();
        }
        return result;
    }

    private Land land(String id, String owner, String name, String parentId) {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land(id, owner, name, "world", parentId,
                areas, 0, 127, true, 0L, "manual", null);
    }

    private Player admin() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "Admin";
                    }
                    if ("isOp".equals(method.getName())) {
                        return true;
                    }
                    if ("hasPermission".equals(method.getName())) {
                        return true;
                    }
                    if ("toString".equals(method.getName())) {
                        return "Admin";
                    }
                    return null;
                });
    }

    private World world(final String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    if ("toString".equals(method.getName())) {
                        return name;
                    }
                    return null;
                });
    }
}
