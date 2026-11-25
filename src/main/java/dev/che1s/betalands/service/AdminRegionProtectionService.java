package dev.che1s.betalands.service;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.che1s.betalands.BetaLandsPlugin;
import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.AdminRegionPermission;
import dev.che1s.betalands.model.LandFlag;
import dev.che1s.betalands.model.Rect2i;

public final class AdminRegionProtectionService {
    private final BetaLandsPlugin plugin;
    private final AdminRegionManager regions;

    public AdminRegionProtectionService(BetaLandsPlugin plugin,
                                        AdminRegionManager regions) {
        this.plugin = plugin;
        this.regions = regions;
    }

    public AdminProtectedRegion findAt(Location location) {
        return regions.findAt(location);
    }

    public boolean canUse(Player player, Location location,
                          AdminRegionPermission permission) {
        AdminProtectedRegion region = regions.findAt(location);
        return region == null || canUse(player, region, permission);
    }

    public boolean canUse(Player player, AdminProtectedRegion region,
                          AdminRegionPermission permission) {
        return region == null || isBypassed(player)
                || region.hasPermission(player == null ? null : player.getName(),
                permission);
    }

    public boolean canUseArea(Player player, String worldName,
                              List<Rect2i> areas, int minY, int maxY,
                              boolean threeDimensional,
                              AdminRegionPermission permission) {
        for (AdminProtectedRegion region : regions.findIntersecting(
                worldName, areas, minY, maxY, threeDimensional)) {
            if (!canUse(player, region, permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean isBypassed(Player player) {
        return player != null && plugin.getLandManager() != null
                && plugin.getLandManager().hasBypass(player);
    }

    public boolean flag(AdminProtectedRegion region, LandFlag flag) {
        if (region == null || plugin.getLandManager() == null) {
            return true;
        }
        return region.flag(flag,
                plugin.getLandManager().getEffectiveLandFlag(null, flag));
    }
}
