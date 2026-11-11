package dev.che1s.betalands.service;

public final class LandImportResult {
    private final int added;
    private final int updated;
    private final int conflicts;

    public LandImportResult(int added, int updated, int conflicts) {
        this.added = added;
        this.updated = updated;
        this.conflicts = conflicts;
    }

    public int getAdded() {
        return added;
    }

    public int getUpdated() {
        return updated;
    }

    public int getConflicts() {
        return conflicts;
    }
}
