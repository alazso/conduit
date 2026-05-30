package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * A straightforward immutable {@link Currency} implementation suitable for
 * providers, bridges, and tests that do not need bespoke formatting.
 *
 * @param id            the machine-readable id
 * @param singularName  the singular display name
 * @param pluralName    the plural display name
 * @param symbol        the display symbol
 * @param decimalPlaces the permitted fractional precision
 * @param isDefault     whether this is the economy's default currency
 */
@ApiStatus.AvailableSince("1.0.0")
public record SimpleCurrency(
        @NotNull String id,
        @NotNull String singularName,
        @NotNull String pluralName,
        @NotNull String symbol,
        int decimalPlaces,
        boolean isDefault
) implements Currency {

    /**
     * Convenience factory for a default currency with matching singular/plural
     * derived from the symbol.
     *
     * @param id            the currency id
     * @param symbol        the display symbol
     * @param decimalPlaces the permitted fractional precision
     * @return a default {@link SimpleCurrency}
     */
    public static @NotNull SimpleCurrency ofDefault(@NotNull String id, @NotNull String symbol, int decimalPlaces) {
        return new SimpleCurrency(id, id, id, symbol, decimalPlaces, true);
    }

    @Override
    public @NotNull String format(@NotNull BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimalPlaces > 0) {
            pattern.append('.');
            pattern.append("0".repeat(decimalPlaces));
        }
        DecimalFormat formatter = new DecimalFormat(pattern.toString(), symbols);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return symbol + formatter.format(amount);
    }
}
