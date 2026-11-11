package dev.che1s.betalands.listener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.LandPermission;
import dev.che1s.betalands.permission.PermissionNodes;
import dev.che1s.betalands.service.AccessCheck;
import dev.che1s.betalands.service.ClaimResult;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.service.TerritoryTracker;

public final class ProtectionListener implements Listener {
    private final BetaLandsPlugin plugin;
    private final LandManager lands;
    private final Map<String, Long> lastMessageAt = new HashMap<String, Long>();
    private final TerritoryTracker territories = new TerritoryTracker();

    public ProtectionListener(BetaLandsPlugin plugin) {
        this.plugin = plugin;
        this.lands = plugin.getLandManager();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            territories.remember(player.getName(), lands.findLandAt(player.getLocation()));
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        AccessCheck check = lands.checkBuildPermission(
                event.getPlayer(), event.getBlock(), LandPermission.BREAK);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(event.getPlayer(), check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        AccessCheck check = lands.checkBuildPermission(
                event.getPlayer(), event.getBlockPlaced(), LandPermission.BUILD);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(event.getPlayer(), check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        AccessCheck check = lands.checkBuildPermission(
                event.getPlayer(), event.getBlockClicked(), LandPermission.BUILD);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(event.getPlayer(), check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        AccessCheck check = lands.checkBuildPermission(
                event.getPlayer(), event.getBlockClicked(), LandPermission.BREAK);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(event.getPlayer(), check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block below = event.getPlayer().getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (isTrampleBlock(below.getTypeId())) {
                Land land = lands.findLandAt(below);
                if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.TRAMPLING)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.getClickedBlock() != null) {
                Land land = lands.findLandAt(event.getClickedBlock());
                if (land != null) {
                    AccessCheck check = lands.checkLandPermission(
                            event.getPlayer(), land, LandPermission.USE);
                    if (!check.isAllowed()) {
                        event.setCancelled(true);
                        deny(event.getPlayer(), check);
                    }
                }
            }
            return;
        }
        if (event.getClickedBlock() != null && event.getItem() != null
                && event.getItem().getTypeId() == 290
                && (event.getAction() == Action.LEFT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (!lands.hasActionPermission(event.getPlayer(),
                    PermissionNodes.CREATE_SUBCLAIM)) {
                plugin.getMessages().send(event.getPlayer(), "common.no-permission",
                        "&cYou do not have permission.");
                event.setCancelled(true);
                return;
            }
            Block selected = event.getClickedBlock();
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getSelections().setFirst(event.getPlayer().getName(), selected.getLocation());
                plugin.getMessages().send(event.getPlayer(), "selection.set",
                        "&a{pos} set to {x}, {y}, {z}.",
                        "pos", "POS1",
                        "x", Integer.toString(selected.getX()),
                        "y", Integer.toString(selected.getY()),
                        "z", Integer.toString(selected.getZ()));
            } else {
                plugin.getSelections().setSecond(event.getPlayer().getName(), selected.getLocation());
                plugin.getMessages().send(event.getPlayer(), "selection.set",
                        "&a{pos} set to {x}, {y}, {z}.",
                        "pos", "POS2",
                        "x", Integer.toString(selected.getX()),
                        "y", Integer.toString(selected.getY()),
                        "z", Integer.toString(selected.getZ()));
            }
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Land land = lands.findLandAt(block);
        if (land == null) {
            return;
        }
        LandPermission permission = isContainer(block.getTypeId())
                ? LandPermission.CONTAINER : LandPermission.USE;
        AccessCheck check = lands.checkLandPermission(event.getPlayer(), land,
                permission);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(event.getPlayer(), check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Land land = lands.findLandAt(block);
            if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.EXPLOSION)) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Land land = lands.findLandAt(event.getBlock());
        Player player = event.getPlayer();
        if (player != null) {
            AccessCheck check = lands.checkBuildPermission(
                    player, event.getBlock(), LandPermission.BUILD);
            if (!check.isAllowed()) {
                event.setCancelled(true);
                deny(player, check);
                return;
            }
        }
        if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Land land = lands.findLandAt(event.getBlock());
        if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.FIRE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Land from = lands.findLandAt(event.getBlock());
        Land to = lands.findLandAt(event.getToBlock());
        if (to != null && !lands.getEffectiveLandFlag(to, LandFlag.FLUID)
                && (from == null || !from.getId().equals(to.getId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Entity damagerEntity = event.getDamager();
        if (!(damagerEntity instanceof Player)) {
            return;
        }
        Player attacker = (Player) damagerEntity;
        if (attacker.getName().equalsIgnoreCase(victim.getName()) || lands.hasBypass(attacker)) {
            return;
        }
        Land land = lands.findLandAt(victim.getLocation());
        if (land == null) {
            return;
        }
        AccessCheck fees = lands.checkFees(attacker, land);
        if (!fees.isAllowed()) {
            event.setCancelled(true);
            deny(attacker, fees);
            return;
        }
        if (lands.getEffectiveLandFlag(land, LandFlag.PVP)) {
            return;
        }
        AccessCheck check = lands.checkLandPermission(attacker, land,
                LandPermission.PVP);
        if (!check.isAllowed()) {
            event.setCancelled(true);
            deny(attacker, check);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        Land land = lands.findLandAt(event.getLocation());
        if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.MOBS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onCreatureTrample(EntityInteractEvent event) {
        if (event.getEntity() instanceof Player || !isTrampleBlock(event.getBlock().getTypeId())) {
            return;
        }
        Land land = lands.findLandAt(event.getBlock());
        if (land != null && !lands.getEffectiveLandFlag(land, LandFlag.TRAMPLING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = Event.Priority.Monitor)
    public void onJoin(PlayerJoinEvent event) {
        lands.startSession(event.getPlayer());
        lands.syncPlayerLevel(event.getPlayer());
        territories.remember(event.getPlayer().getName(),
                lands.findLandAt(event.getPlayer().getLocation()));
        if (plugin.getFeeNotifications() != null) {
            plugin.getFeeNotifications().scheduleJoinReminder(event.getPlayer());
        }
    }

    @EventHandler(priority = Event.Priority.Monitor)
    public void onQuit(PlayerQuitEvent event) {
        lands.stopSession(event.getPlayer());
        lands.stopAutoClaim(event.getPlayer());
        territories.forget(event.getPlayer().getName());
        lastMessageAt.remove(event.getPlayer().getName().toLowerCase(java.util.Locale.ROOT));
    }

    @EventHandler(priority = Event.Priority.Monitor)
    public void onMove(PlayerMoveEvent event) {
        if (event.isCancelled() || event.getTo() == null) {
            return;
        }
        boolean movedToAnotherBlock = event.getFrom().getWorld() != event.getTo().getWorld()
                || event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();

        if (lands.isAutoClaimEnabled(event.getPlayer())) {
            updateAutoClaim(event);
        }
        if (movedToAnotherBlock) {
            updateTerritory(event.getPlayer(), event.getTo());
        }
    }

    private void updateAutoClaim(PlayerMoveEvent event) {
        int fromChunkX = event.getFrom().getBlockX() >> 4;
        int fromChunkZ = event.getFrom().getBlockZ() >> 4;
        int toChunkX = event.getTo().getBlockX() >> 4;
        int toChunkZ = event.getTo().getBlockZ() >> 4;
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) {
            return;
        }
        ClaimResult result = lands.tryAutoClaim(event.getPlayer());
        if (result.getState() == ClaimResult.State.CLAIMED) {
            Land land = result.getLand();
            plugin.getMessages().send(event.getPlayer(), "claim.auto-claimed",
                    "&aAuto-claim is active and claimed the current chunk for {land}{cost}.",
                    "land", land.getDisplayName(),
                    "cost", result.getPrice() <= 0.0D ? ""
                            : ", cost " + plugin.getEconomy().format(result.getPrice()));
            plugin.getAuditLog().logLand("CLAIM_CHUNK_AUTO", event.getPlayer().getName(),
                    null, land, "Auto-claimed one chunk while moving",
                    lands.isAdmin(event.getPlayer()));
            plugin.getAuditLog().logEconomy("CLAIM_CHUNK_FEE_AUTO",
                    event.getPlayer().getName(), land, result.getPrice(), land.getBankBalance(),
                    "Auto-claim chunk fee", lands.isAdmin(event.getPlayer()));
            plugin.scheduleMapRefresh();
        } else if (result.getState() == ClaimResult.State.FAILED) {
            lands.stopAutoClaim(event.getPlayer());
            plugin.getMessages().send(event.getPlayer(), "claim.auto-stopped-v2",
                    "&cAuto-claim stopped: {reason}",
                    "reason", result.getError());
        }
    }

    @EventHandler(priority = Event.Priority.Monitor, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            updateTerritory(event.getPlayer(), event.getTo());
        }
    }

    private void updateTerritory(Player player, Location location) {
        Land current = lands.findLandAt(location);
        if (!plugin.getSettings().areTerritoryMessagesEnabled()) {
            territories.remember(player.getName(), current);
            return;
        }
        TerritoryTracker.Change change = territories.update(player.getName(), current);
        if (!change.changed()) {
            return;
        }
        Land previous = change.getPrevious();
        if (previous != null) {
            plugin.getMessages().send(player, "territory.leave",
                    "&7~ Leaving &b{land}&7 ~",
                    "land", previous.getTerritoryName());
        }
        if (current != null) {
            plugin.getMessages().send(player, "territory.enter",
                    "&e~ Entering &b{land}&e. Owner: &a{owner}&e ~",
                    "land", current.getTerritoryName(),
                    "owner", current.getOwner());
        } else if (plugin.getSettings().shouldShowWildernessMessage()) {
            plugin.getMessages().send(player, "territory.wilderness",
                    "&7~ Wilderness ~");
        }
    }

    private void deny(Player player, AccessCheck check) {
        long now = System.currentTimeMillis();
        String key = player.getName().toLowerCase(java.util.Locale.ROOT);
        Long previous = lastMessageAt.get(key);
        if (previous != null && now - previous.longValue() < 1000L) {
            return;
        }
        lastMessageAt.put(key, Long.valueOf(now));
        if (check.isOutsideLocked()) {
            long remaining = Math.max(0L, plugin.getSettings().getOutsideBuildHours() * 3600L
                    - lands.getOrCreatePlayerData(player).getTotalSeconds(now));
            plugin.getMessages().send(player, "protection.outside-locked",
                    "&cOutside building is locked. Remaining online time: {remaining}.",
                    "remaining", dev.che1s.betalands.util.Text.duration(remaining));
            return;
        }
        if (check.isRentDebt()) {
            plugin.getMessages().send(player, "protection.rent-debt",
                    "&cYou owe rent for this subclaim and cannot build or break blocks there.");
            return;
        }
        if (check.isMaintenanceDebt()) {
            plugin.getMessages().send(player, "protection.maintenance-debt",
                    "&cThis land has unpaid maintenance fees and cannot be modified.");
            return;
        }
        Land land = check.getLand();
        if (land != null) {
            plugin.getMessages().send(player, "protection.denied",
                    "&cYou do not have the required permission in {land}.",
                    "land", land.getDisplayName());
        } else {
            plugin.getMessages().send(player, "protection.generic-denied",
                    "&cBlocked by BetaLands protection.");
        }
    }

    private boolean isContainer(int typeId) {
        return typeId == 23 || typeId == 54 || typeId == 61 || typeId == 58;
    }

    private boolean isTrampleBlock(int typeId) {
        return typeId == 59 || typeId == 60;
    }
}
