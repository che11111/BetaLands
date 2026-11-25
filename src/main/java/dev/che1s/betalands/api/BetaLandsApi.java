package dev.che1s.betalands.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public interface BetaLandsApi {
    int API_VERSION = 7;

    static BetaLandsApi get() {
        RegisteredServiceProvider<BetaLandsApi> registration =
                Bukkit.getServicesManager().getRegistration(BetaLandsApi.class);
        return registration == null ? null : registration.getProvider();
    }

    int getApiVersion();

    Optional<LandInfo> getLand(String nameOrId);

    Optional<LandInfo> getLandAt(String worldName, int x, int y, int z);

    List<LandInfo> getLands();

    List<LandInfo> getLandsOwnedBy(String playerName);

    List<PlayerLandAccess> getPlayerAccesses(String playerName);

    List<PlayerLandAccess> findLandsWithPermission(String playerName,
                                                   LandPermission permission);

    Optional<AdminRegionInfo> getAdminRegion(String nameOrId);

    Optional<AdminRegionInfo> getAdminRegionAt(String worldName,
                                               int x, int y, int z);

    List<AdminRegionInfo> getAdminRegions();

    boolean canUseAdminRegion(String playerName, String nameOrId,
                              AdminRegionPermission permission);

    double getServerMaintenanceBankBalance();
}
