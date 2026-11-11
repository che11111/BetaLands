package dev.che1s.betalands.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.junit.Test;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.model.Rect2i;

public class AccessPolicyTest {
    @Test
    public void maintenanceDebtBlocksMembershipChanges() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        main.setBankBalance(-5.0D);

        assertFalse(manager.canChangeFamilyMembers(main));
        assertTrue(manager.forceAddMemberDirect(main, "Builder"));
    }

    @Test
    public void clearedBalanceRestoresMembershipChanges() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        main.setBankBalance(-1.0D);
        assertFalse(manager.canChangeFamilyMembers(main));

        main.setBankBalance(0.0D);
        assertTrue(manager.canChangeFamilyMembers(main));
        assertTrue(manager.forceAddMemberDirect(main, "Builder"));
    }

    @Test
    public void maintenanceDebtBlocksContainerAndUseAccess() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        main.setMemberPermissions("Builder",
                java.util.EnumSet.of(LandPermission.USE,
                        LandPermission.CONTAINER));
        main.setBankBalance(-5.0D);

        AccessCheck overflow = manager.checkLandPermission(
                player("Builder"), main, LandPermission.CONTAINER);
        AccessCheck door = manager.checkLandPermission(
                player("Builder"), main, LandPermission.USE);
        assertFalse(overflow.isAllowed());
        assertTrue(overflow.isMaintenanceDebt());
        assertFalse(door.isAllowed());
        assertTrue(door.isMaintenanceDebt());
    }

    @Test
    public void maintenanceDebtPreventsPermissionFromAddingBuildAccess() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        PermissionService permissions =
                new PermissionService(null, manager);
        MembershipService membership = new MembershipService(
                null, manager, permissions,
                new AccessPolicy(null, manager, permissions));
        main.setBankBalance(-1.0D);

        assertFalse(membership.canGrantBuildAccess(main, "Builder"));
    }

    @Test
    public void forcePermissionAndRemovalBypassDebtChecks() {
        LandManager manager = new LandManager(null);
        Land main = land();
        manager.addLoadedLand(main);
        PermissionService permissions =
                new PermissionService(null, manager);
        MembershipService membership = new MembershipService(
                null, manager, permissions,
                new AccessPolicy(null, manager, permissions));
        main.setBankBalance(-1.0D);
        Player admin = adminPlayer();

        assertTrue(membership.forceSetPermission(
                admin, main, "Builder", LandPermission.BUILD, true));
        assertTrue(manager.hasPermissionInLand("Builder", main,
                LandPermission.BUILD, new java.util.HashSet<String>()));
        assertTrue(membership.forceRemoveMember(admin, main, "Builder"));
        assertFalse(manager.hasPermissionInLand("Builder", main,
                LandPermission.BUILD, new java.util.HashSet<String>()));
    }

    private Player player(final String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    if ("isOp".equals(method.getName())
                            || "hasPermission".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return name;
                    }
                    return null;
                });
    }

    private Player adminPlayer() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return "Admin";
                    }
                    if ("isOp".equals(method.getName())
                            || "hasPermission".equals(method.getName())) {
                        return true;
                    }
                    if ("toString".equals(method.getName())) {
                        return "Admin";
                    }
                    return null;
                });
    }

    private Land land() {
        List<Rect2i> areas = new ArrayList<Rect2i>();
        areas.add(new Rect2i(0, 0, 15, 15));
        return new Land("land", "Alice", "Town", "world", null,
                areas, 0, 127, true, 1L, "manual", null);
    }
}
