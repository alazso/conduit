package so.alaz.conduit.core.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.model.Currency;

import java.math.BigDecimal;

/**
 * Synchronous, boundary-level validation of economy amounts.
 *
 * <p>These checks throw {@link IllegalArgumentException} at the call site rather
 * than failing the returned future, because they catch caller programming errors
 * (null/negative/zero magnitude, scale overflow), not runtime money outcomes.
 */
@ApiStatus.Internal
public final class AmountValidator {

    /**
     * The largest amount any single operation may move or set. Guards against
     * overflow and obviously-bogus values.
     */
    public static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000000000000"); // 1e15

    private AmountValidator() {
    }

    /**
     * Validate a strictly-positive magnitude for deposit/withdraw/transfer.
     *
     * @param amount   the amount (must be {@code > 0})
     * @param currency the active currency defining allowed precision
     * @throws IllegalArgumentException if the amount is null, non-positive,
     *                                  over-scaled, or above the maximum
     */
    public static void validateMagnitude(@Nullable BigDecimal amount, @NotNull Currency currency) {
        requireNonNull(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive (negative is not implicit reversal; zero is a no-op caller bug)");
        }
        checkScale(amount, currency);
        checkMaximum(amount);
    }

    /**
     * Validate a non-negative absolute balance for {@code set()}.
     *
     * @param amount   the amount (must be {@code >= 0})
     * @param currency the active currency defining allowed precision
     * @throws IllegalArgumentException if the amount is null, negative,
     *                                  over-scaled, or above the maximum
     */
    public static void validateAbsolute(@Nullable BigDecimal amount, @NotNull Currency currency) {
        requireNonNull(amount);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("set() amount must be non-negative");
        }
        checkScale(amount, currency);
        checkMaximum(amount);
    }

    private static void requireNonNull(@Nullable BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
    }

    private static void checkScale(BigDecimal amount, Currency currency) {
        if (amount.scale() > currency.decimalPlaces()) {
            throw new IllegalArgumentException(
                    "Amount scale " + amount.scale() + " exceeds currency precision " + currency.decimalPlaces());
        }
    }

    private static void checkMaximum(BigDecimal amount) {
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum");
        }
    }
}
