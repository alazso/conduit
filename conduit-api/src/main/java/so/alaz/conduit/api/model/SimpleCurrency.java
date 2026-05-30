package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

    // Per-scale, per-thread formatters: DecimalFormat is not thread-safe, and
    // rebuilding it on every format() call is wasteful on hot paths (scoreboards,
    // PlaceholderAPI). Keyed by decimalPlaces; the symbol is applied separately.
    private static final Map<Integer, ThreadLocal<DecimalFormat>> FORMATTERS = new ConcurrentHashMap<>();

    /**
     * Canonical constructor; validates components at the boundary.
     *
     * @throws IllegalArgumentException if {@code decimalPlaces} is negative
     */
    public SimpleCurrency {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(singularName, "singularName");
        Objects.requireNonNull(pluralName, "pluralName");
        Objects.requireNonNull(symbol, "symbol");
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("decimalPlaces must be non-negative, got " + decimalPlaces);
        }
    }

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
        return symbol + formatter(decimalPlaces).format(amount);
    }

    private static DecimalFormat formatter(int decimalPlaces) {
        return FORMATTERS.computeIfAbsent(decimalPlaces, dp -> ThreadLocal.withInitial(() -> {
            StringBuilder pattern = new StringBuilder("#,##0");
            if (dp > 0) {
                pattern.append('.').append("0".repeat(dp));
            }
            DecimalFormat formatter = new DecimalFormat(pattern.toString(), new DecimalFormatSymbols(Locale.ROOT));
            formatter.setRoundingMode(RoundingMode.HALF_UP);
            return formatter;
        })).get();
    }
}
