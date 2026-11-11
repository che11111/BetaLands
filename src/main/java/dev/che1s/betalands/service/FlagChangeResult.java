package dev.che1s.betalands.service;

import dev.che1s.betalands.model.Land;
import dev.che1s.betalands.model.LandFlag;

/**
 * Result of changing one protection flag.
 */
public final class FlagChangeResult {
    private final boolean success;
    private final String error;
    private final Land land;
    private final LandFlag flag;
    private final boolean effective;
    private final String source;

    private FlagChangeResult(boolean success, String error, Land land,
                             LandFlag flag, boolean effective, String source) {
        this.success = success;
        this.error = error;
        this.land = land;
        this.flag = flag;
        this.effective = effective;
        this.source = source;
    }

    public static FlagChangeResult success(Land land, LandFlag flag,
                                           boolean effective, String source) {
        return new FlagChangeResult(true, null, land, flag, effective, source);
    }

    public static FlagChangeResult error(String error) {
        return new FlagChangeResult(false, error, null, null, false, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public Land getLand() {
        return land;
    }

    public LandFlag getFlag() {
        return flag;
    }

    public boolean isEffective() {
        return effective;
    }

    public String getSource() {
        return source;
    }
}
