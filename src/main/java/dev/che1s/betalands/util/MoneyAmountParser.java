package dev.che1s.betalands.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Bounds player-entered money strings before BigDecimal conversion.
 */
public final class MoneyAmountParser {
    private static final int MAX_INPUT_LENGTH = 32;
    private static final Pattern DECIMAL = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)?");

    private MoneyAmountParser() {
    }

    public static Result parse(String input, int maxDecimalPlaces) {
        String value = input == null ? "" : input.trim();
        if (value.length() == 0 || value.length() > MAX_INPUT_LENGTH
                || !DECIMAL.matcher(value).matches()) {
            return Result.invalid();
        }
        int dot = value.indexOf('.');
        int decimals = dot < 0 ? 0 : value.length() - dot - 1;
        if (decimals > Math.max(0, maxDecimalPlaces)) {
            return Result.tooManyDecimals();
        }
        try {
            BigDecimal decimal = new BigDecimal(value);
            double amount = decimal.doubleValue();
            if (Double.isNaN(amount) || Double.isInfinite(amount)) {
                return Result.invalid();
            }
            return Result.success(amount);
        } catch (NumberFormatException exception) {
            return Result.invalid();
        }
    }

    public enum Status {
        SUCCESS,
        INVALID,
        TOO_MANY_DECIMALS
    }

    public static final class Result {
        private final Status status;
        private final double amount;

        private Result(Status status, double amount) {
            this.status = status;
            this.amount = amount;
        }

        private static Result success(double amount) {
            return new Result(Status.SUCCESS, amount);
        }

        private static Result invalid() {
            return new Result(Status.INVALID, 0.0D);
        }

        private static Result tooManyDecimals() {
            return new Result(Status.TOO_MANY_DECIMALS, 0.0D);
        }

        public Status getStatus() {
            return status;
        }

        public double getAmount() {
            return amount;
        }
    }
}
