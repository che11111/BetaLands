package dev.che1s.betalands.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.che1s.betalands.model.PlayerData;

public class CommandRoutingTest {
    @Test
    public void routesCommandsToFocusedHandlers() {
        assertEquals("player", BetaLandsCommand.route("create"));
        assertEquals("player", BetaLandsCommand.route("level"));
        assertEquals("bank", BetaLandsCommand.route("BANK"));
        assertEquals("bank-all", BetaLandsCommand.route("bank-all"));
        assertEquals("rent", BetaLandsCommand.route("rent"));
        assertEquals("admin", BetaLandsCommand.route("admin"));
        assertEquals("admin-land",
                BetaLandsCommand.route("admin-land"));
        assertEquals("admin-land-force",
                BetaLandsCommand.route("admin-land-force"));
        assertEquals("player", BetaLandsCommand.route("admin-force"));
        assertEquals("unknown", BetaLandsCommand.route(""));
    }

    @Test
    public void administratorsUsePlayerAddOnlyForTheirOwnLand() {
        assertEquals(true,
                MembershipCommandHandler.requiresAdminCommand(
                        true, "add", false));
        assertEquals(false,
                MembershipCommandHandler.requiresAdminCommand(
                        true, "add", true));
        assertEquals(false,
                MembershipCommandHandler.requiresAdminCommand(
                        false, "add", false));
        assertEquals(false,
                MembershipCommandHandler.requiresAdminCommand(
                        true, "kick", false));
    }

    @Test
    public void parsesFiniteAdminTeleportCoordinates() {
        assertEquals(12.5D, AdminCommandHandler.coordinate("12.5"), 0.0D);
        assertEquals(-64.0D, AdminCommandHandler.coordinate("-64"), 0.0D);
        assertNull(AdminCommandHandler.coordinate("NaN"));
        assertNull(AdminCommandHandler.coordinate("Infinity"));
        assertNull(AdminCommandHandler.coordinate("not-a-number"));
    }

    @Test
    public void readsWorldAfterCoordinates() {
        assertNull(AdminCommandHandler.teleportWorldName(new String[]{
                "admin", "tp", "10", "64", "20"}));
        assertEquals("world_nether",
                AdminCommandHandler.teleportWorldName(new String[]{
                        "admin", "tp", "10", "64", "20", "world_nether"}));
    }

    @Test
    public void parsesRecentCountsAndExplicitLogPages() {
        AdminCommandHandler.LogQuery recent =
                AdminCommandHandler.parseLogQuery(
                        new String[]{"admin", "logs", "100"});
        assertNull(recent.getPlayerFilter());
        assertEquals(100, recent.getSelection());
        assertFalse(recent.isPageMode());

        AdminCommandHandler.LogQuery firstPage =
                AdminCommandHandler.parseLogQuery(
                        new String[]{"admin", "logs", "p1"});
        assertEquals(1, firstPage.getSelection());
        assertTrue(firstPage.isPageMode());

        AdminCommandHandler.LogQuery player =
                AdminCommandHandler.parseLogQuery(
                        new String[]{"admin", "logs", "player", "Alice", "50"});
        assertEquals(AdminCommandHandler.LogMode.PLAYER, player.getMode());
        assertEquals("Alice", player.getPlayerFilter());
        assertEquals(50, player.getSelection());
        assertFalse(player.isPageMode());

        AdminCommandHandler.LogQuery land =
                AdminCommandHandler.parseLogQuery(
                        new String[]{"admin", "logs", "land", "AAA", "p3"});
        assertEquals(AdminCommandHandler.LogMode.LAND, land.getMode());
        assertEquals("AAA", land.getPlayerFilter());
        assertEquals(3, land.getSelection());
        assertTrue(land.isPageMode());

        assertFalse(AdminCommandHandler.parseLogQuery(
                new String[]{"admin", "logs", "Alice"}).isValid());
        assertFalse(AdminCommandHandler.parseLogQuery(
                new String[]{"admin", "logs", "p0"}).isValid());
        assertFalse(AdminCommandHandler.parseLogQuery(
                new String[]{"admin", "logs", "201"}).isValid());
    }

    @Test
    public void removedAliasesAreNotPlayerCommands() {
        PlayerCommandHandler handler = new PlayerCommandHandler(null);
        String[] removed = {"remove", "join", "decline", "reject",
                "subclaim", "permission", "sethome", "home", "removeclaim",
                "owner", "transfer", "transferowner", "time", "log"};
        for (String alias : removed) {
            assertFalse(alias, handler.handle(alias, null, null));
        }
    }

    @Test
    public void publicTeleportAndFeeRulesAreSeparated() {
        assertTrue(TeleportCommandHandler.canAccessTeleport(
                false, false, false, true));
        assertFalse(TeleportCommandHandler.canAccessTeleport(
                false, false, false, false));
        assertTrue(TeleportCommandHandler.canAccessTeleport(
                false, true, false, false));
        assertFalse(TeleportCommandHandler.appliesFeeRestrictions(
                false, false, false));
        assertTrue(TeleportCommandHandler.appliesFeeRestrictions(
                false, true, false));
        assertFalse(TeleportCommandHandler.appliesFeeRestrictions(
                true, true, true));
    }

    @Test
    public void parsesMapRefreshTargets() {
        assertEquals(AdminCommandHandler.MapRefreshTarget.ALL,
                AdminCommandHandler.parseRefreshTarget(
                        new String[]{"admin", "refresh-map"}));
        assertEquals(AdminCommandHandler.MapRefreshTarget.DYNMAP,
                AdminCommandHandler.parseRefreshTarget(
                        new String[]{"admin", "refresh-map", "dynmap"}));
        assertEquals(AdminCommandHandler.MapRefreshTarget.BLUEMAP,
                AdminCommandHandler.parseRefreshTarget(
                        new String[]{"admin", "refresh-map", "bluemap"}));
        assertNull(AdminCommandHandler.parseRefreshTarget(
                new String[]{"admin", "refresh-map", "invalid"}));
    }

    @Test
    public void deletionConfirmationRequiresAnExplicitLandName() {
        assertFalse(OwnershipCommandHandler.deleteConfirmationHasTarget(
                new String[]{"delete", "confirm"}));
        assertFalse(OwnershipCommandHandler.deleteConfirmationHasTarget(
                new String[]{"delete", "confirm", " "}));
        assertTrue(OwnershipCommandHandler.deleteConfirmationHasTarget(
                new String[]{"delete", "confirm", "AAA"}));
    }

    @Test
    public void offlineAdminLevelReadsStoredOrManagedSnapshot() {
        assertEquals(InfoCommandHandler.LevelSource.OFFLINE_DEFAULT,
                InfoCommandHandler.levelSource(null, null));

        PlayerData stored = new PlayerData("builder", "Builder", 0L);
        stored.setLevelId("vip");
        assertEquals(InfoCommandHandler.LevelSource.OFFLINE_SNAPSHOT,
                InfoCommandHandler.levelSource(null, stored));

        PlayerData managed = new PlayerData("builder", "Builder", 0L);
        managed.setManagedLevel("svip", 0L);
        assertEquals(InfoCommandHandler.LevelSource.MANAGED,
                InfoCommandHandler.levelSource(null, managed));
    }

    @Test
    public void rentDepositSyntaxIsGlobalOnly() {
        assertTrue(RentCommandHandler.validDepositSyntax(
                new String[]{"rent", "deposit", "100"}));
        assertFalse(RentCommandHandler.validDepositSyntax(
                new String[]{"rent", "deposit", "100", "AAA"}));
        assertFalse(RentCommandHandler.validDepositSyntax(
                new String[]{"rent", "deposit", " "}));
        assertTrue(RentCommandHandler.validWithdrawSyntax(
                new String[]{"rent", "withdraw", "100"}));
        assertFalse(RentCommandHandler.validWithdrawSyntax(
                new String[]{"rent", "withdraw", "100", "AAA"}));
    }
}
