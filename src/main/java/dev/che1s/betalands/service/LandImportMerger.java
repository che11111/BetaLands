package dev.che1s.betalands.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.util.Text;

/**
 * Merges converted Towny lands into the active in-memory catalog.
 */
final class LandImportMerger {
    private final LandIndex index;

    LandImportMerger(LandIndex index) {
        this.index = index;
    }

    LandImportResult merge(List<Land> imported) {
        int added = 0;
        int updated = 0;
        int conflicts = 0;
        Set<String> importedIds = new HashSet<String>();
        Set<String> previousTownyIds = new HashSet<String>();
        Map<String, Land> previousTownyLands = new HashMap<String, Land>();
        for (Land existing : index.getAll()) {
            if (existing.getSource().startsWith("towny:")) {
                previousTownyIds.add(existing.getId());
                if (!existing.isSubclaim()) {
                    previousTownyLands.put(Text.key(existing.getSource()), existing);
                }
            }
        }
        for (String previousId : previousTownyIds) {
            index.remove(previousId);
        }
        Set<String> occupiedNames = new HashSet<String>();
        for (Land existing : index.getAll()) {
            occupiedNames.add(Text.key(existing.getTerritoryName()));
        }
        Map<String, Land> importedById = new HashMap<String, Land>();
        for (Land land : imported) {
            if (!importedIds.add(land.getId())) {
                conflicts++;
                continue;
            }
            Land importedParent = land.getParentId() == null
                    ? null : importedById.get(land.getParentId());
            if (land.getParentId() != null && importedParent == null) {
                conflicts++;
                continue;
            }
            String candidateName = importedParent == null
                    ? land.getName()
                    : importedParent.getTerritoryName() + "." + land.getName();
            if (!occupiedNames.add(Text.key(candidateName))) {
                conflicts++;
                continue;
            }
            Land existing = index.get(land.getId());
            if (existing == null) {
                Land previous = previousTownyLands.get(
                        Text.key(land.getSource()));
                if (previous != null) {
                    land.setBankBalance(previous.getBankBalance());
                    land.setLastMaintenanceAt(previous.getLastMaintenanceAt());
                    land.setLevelId(previous.getLevelId());
                    land.setRentEnabled(previous.isRentEnabled());
                    land.setRentAmount(previous.getRentAmount());
                    land.setLastRentAt(previous.getLastRentAt());
                }
                index.add(land);
                importedById.put(land.getId(), land);
                if (previous != null) {
                    updated++;
                } else {
                    added++;
                }
            } else {
                conflicts++;
            }
        }
        index.reindexAfterMutation();
        return new LandImportResult(added, updated, conflicts);
    }
}
