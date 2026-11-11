package dev.che1s.betalands.service;

import dev.che1s.betalands.model.Land;

/**
 * Result of an automatic chunk-claim attempt.
 */
public final class ClaimResult {
    private final State state;
    private final Land land;
    private final String error;
    private final double price;

    private ClaimResult(State state, Land land, String error, double price) {
        this.state = state;
        this.land = land;
        this.error = error;
        this.price = price;
    }

    public static ClaimResult inactive() {
        return new ClaimResult(State.INACTIVE, null, null, 0.0D);
    }

    public static ClaimResult alreadyOwned() {
        return new ClaimResult(State.ALREADY_OWNED, null, null, 0.0D);
    }

    public static ClaimResult claimed(Land land, double price) {
        return new ClaimResult(State.CLAIMED, land, null, price);
    }

    public static ClaimResult failed(String error) {
        return new ClaimResult(State.FAILED, null, error, 0.0D);
    }

    public State getState() {
        return state;
    }

    public Land getLand() {
        return land;
    }

    public String getError() {
        return error;
    }

    public double getPrice() {
        return price;
    }

    public enum State {
        INACTIVE,
        ALREADY_OWNED,
        CLAIMED,
        FAILED
    }
}
