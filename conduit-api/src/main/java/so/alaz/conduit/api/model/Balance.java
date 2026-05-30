package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable snapshot of an account's balance in a single currency.
 *
 * <p>All monetary values are {@link BigDecimal}; there is no {@code double}
 * representation anywhere in the Conduit API.
 *
 * @param owner    the account owner's UUID
 * @param currency the currency this balance is denominated in
 * @param amount   the balance amount (may be negative if the provider permits it)
 */
@ApiStatus.AvailableSince("1.0.0")
public record Balance(@NotNull UUID owner, @NotNull Currency currency, @NotNull BigDecimal amount) {

    /**
     * Canonical constructor; rejects null components at the boundary.
     */
    public Balance {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
    }

    /**
     * @return {@code true} if the balance is below zero.
     */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
