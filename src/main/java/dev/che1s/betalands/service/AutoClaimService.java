package dev.che1s.betalands.service;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.util.Text;

/**
 * Tracks one auto-claim target per online player.
 */
public final class AutoClaimService {
    private final LandManager lands;
    private final Map<String, String> targets = new HashMap<String, String>();
    private final Map<String, Boolean> forceModes = new HashMap<String, Boolean>();

    public AutoClaimService(LandManager lands) {
        this.lands = lands;
    }

    public Land getTarget(Player player) {
        String id = targets.get(Text.key(player.getName()));
        return id == null ? null : lands.getById(id);
    }

    public boolean isEnabled(Player player) {
        return getTarget(player) != null;
    }

    public void start(Player player, Land land) {
        start(player, land, false);
    }

    public void start(Player player, Land land, boolean force) {
        targets.put(Text.key(player.getName()), land.getId());
        forceModes.put(Text.key(player.getName()), Boolean.valueOf(force));
    }

    public void stop(Player player) {
        targets.remove(Text.key(player.getName()));
        forceModes.remove(Text.key(player.getName()));
    }

    public void clear() {
        targets.clear();
        forceModes.clear();
    }

    public ClaimResult tryClaim(Player player) {
        return tryClaim(player, false);
    }

    public ClaimResult tryClaim(Player player, boolean force) {
        Land target = getTarget(player);
        if (target == null) {
            return ClaimResult.inactive();
        }
        int chunkX = player.getLocation().getBlockX() >> 4;
        int chunkZ = player.getLocation().getBlockZ() >> 4;
        if (lands.landContainsChunk(target, chunkX, chunkZ)) {
            return ClaimResult.alreadyOwned();
        }
        boolean forceMode = force
                || Boolean.TRUE.equals(forceModes.get(
                Text.key(player.getName())));
        CreateResult result =
                lands.claimChunk(player, target, forceMode);
        return result.isSuccess()
                ? ClaimResult.claimed(target, result.getPrice())
                : ClaimResult.failed(result.getError());
    }
}
