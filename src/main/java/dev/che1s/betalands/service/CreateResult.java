package dev.che1s.betalands.service;

import dev.che1s.betalands.model.Land;

/**
 * Result of creating or expanding a land.
 */
public final class CreateResult {
    private final Land land;
    private final String error;
    private final double price;

    private CreateResult(Land land, String error, double price) {
        this.land = land;
        this.error = error;
        this.price = price;
    }

    public static CreateResult success(Land land) {
        return success(land, 0.0D);
    }

    public static CreateResult success(Land land, double price) {
        return new CreateResult(land, null, price);
    }

    public static CreateResult error(String error) {
        return new CreateResult(null, error, 0.0D);
    }

    public boolean isSuccess() {
        return land != null;
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
}
