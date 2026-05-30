package so.alaz.conduit.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;

/**
 * Represents a currency within an economy.
 *
 * <p>Economies that support only a single currency expose it via
 * {@link so.alaz.conduit.api.economy.Economy#defaultCurrency()}. The currency,
 * not the provider, defines the allowed monetary precision via
 * {@link #decimalPlaces()}; amounts whose scale exceeds that precision are
 * rejected at the API boundary.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface Currency {

    /**
     * @return the stable, machine-readable identifier of this currency
     *         (e.g. {@code "dollars"}, {@code "gold"}, {@code "tokens"}).
     */
    @NotNull String id();

    /**
     * @return the singular human-readable name (e.g. {@code "Dollar"}).
     */
    @NotNull String singularName();

    /**
     * @return the plural human-readable name (e.g. {@code "Dollars"}).
     */
    @NotNull String pluralName();

    /**
     * @return the display symbol (e.g. {@code "$"}).
     */
    @NotNull String symbol();

    /**
     * @return the number of fractional digits this currency permits. Amounts
     *         whose {@link BigDecimal#scale()} exceeds this value are rejected.
     */
    int decimalPlaces();

    /**
     * @return {@code true} if this is the economy's default currency.
     */
    boolean isDefault();

    /**
     * Format an amount to a human-readable string.
     *
     * @param amount the amount to format; never {@code null}
     * @return the formatted representation, including symbol/name as appropriate
     */
    @NotNull String format(@NotNull BigDecimal amount);
}
