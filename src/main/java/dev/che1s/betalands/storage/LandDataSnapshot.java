package dev.che1s.betalands.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.AdminProtectedRegion;
import dev.che1s.betalands.model.PlayerData;
import dev.che1s.betalands.model.RentAccount;
import dev.che1s.betalands.service.LandManager;
import dev.che1s.betalands.service.AdminRegionManager;

public final class LandDataSnapshot {
    private final List<Land> lands;
    private final Map<String, PlayerData> players;
    private final Map<String, RentAccount> rentAccounts;
    private final double serverMaintenanceBank;
    private final List<AdminProtectedRegion> adminRegions;

    private LandDataSnapshot(List<Land> lands, Map<String, PlayerData> players,
                             Map<String, RentAccount> rentAccounts,
                             double serverMaintenanceBank,
                             List<AdminProtectedRegion> adminRegions) {
        this.lands = Collections.unmodifiableList(new ArrayList<Land>(lands));
        this.players = Collections.unmodifiableMap(
                new LinkedHashMap<String, PlayerData>(players));
        this.rentAccounts = Collections.unmodifiableMap(
                new LinkedHashMap<String, RentAccount>(rentAccounts));
        this.serverMaintenanceBank = serverMaintenanceBank;
        this.adminRegions = Collections.unmodifiableList(
                new ArrayList<AdminProtectedRegion>(adminRegions));
    }

    public static LandDataSnapshot capture(LandManager manager, long now) {
        return capture(manager, new AdminRegionManager(), now);
    }

    public static LandDataSnapshot capture(LandManager manager,
                                           AdminRegionManager adminRegions,
                                           long now) {
        List<Land> lands = new ArrayList<Land>();
        for (Land land : manager.getPersistedLands()) {
            lands.add(land.copyForImport());
        }
        Map<String, PlayerData> players = new LinkedHashMap<String, PlayerData>();
        for (Map.Entry<String, PlayerData> entry : manager.getPlayers().entrySet()) {
            PlayerData source = entry.getValue();
            PlayerData copy = new PlayerData(source.getKey(), source.getLastName(),
                    source.getTotalSeconds(now));
            copy.setLevelId(source.getLevelId());
            if (source.isLevelManaged()) {
                copy.setManagedLevel(source.getLevelId(), source.getLevelExpiresAt());
            }
            players.put(entry.getKey(), copy);
        }
        Map<String, RentAccount> rentAccounts =
                new LinkedHashMap<String, RentAccount>();
        for (Map.Entry<String, RentAccount> entry
                : manager.getRentAccounts().entrySet()) {
            RentAccount source = entry.getValue();
            rentAccounts.put(entry.getKey(), new RentAccount(
                    source.getPlayerName(), source.getBalance(),
                    source.getDebtLandId()));
        }
        List<AdminProtectedRegion> regions =
                new ArrayList<AdminProtectedRegion>();
        if (adminRegions != null) {
            for (AdminProtectedRegion region : adminRegions.getRegions()) {
                regions.add(region.copyForStorage());
            }
        }
        return new LandDataSnapshot(lands, players, rentAccounts,
                manager.getServerMaintenanceBank(), regions);
    }

    public List<Land> getLands() {
        return lands;
    }

    public Map<String, PlayerData> getPlayers() {
        return players;
    }

    public Map<String, RentAccount> getRentAccounts() {
        return rentAccounts;
    }

    public double getServerMaintenanceBank() {
        return serverMaintenanceBank;
    }

    public List<AdminProtectedRegion> getAdminRegions() {
        return adminRegions;
    }
}
