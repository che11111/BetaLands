package dev.che1s.betalands.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.che1s.betalands.model.Land;

public final class TownyImportReport {
    private final String source;
    private final List<Land> lands;
    private final int towns;
    private final int plots;
    private final long claimedBlocks;
    private final List<String> warnings;

    public TownyImportReport(String source, List<Land> lands, int towns, int plots,
                             long claimedBlocks, List<String> warnings) {
        this.source = source;
        this.lands = new ArrayList<Land>(lands);
        this.towns = towns;
        this.plots = plots;
        this.claimedBlocks = claimedBlocks;
        this.warnings = new ArrayList<String>(warnings);
    }

    public String getSource() {
        return source;
    }

    public List<Land> getLands() {
        return Collections.unmodifiableList(lands);
    }

    public int getTowns() {
        return towns;
    }

    public int getPlots() {
        return plots;
    }

    public long getClaimedBlocks() {
        return claimedBlocks;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
