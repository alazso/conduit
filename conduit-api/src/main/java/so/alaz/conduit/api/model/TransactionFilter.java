package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Filter criteria for transaction-history queries.
 *
 * @param type     restrict to a transaction type, or {@code null} for any
 * @param currency restrict to a currency, or {@code null} for any
 * @param after    only transactions at or after this instant, or {@code null}
 * @param before   only transactions at or before this instant, or {@code null}
 * @param limit    maximum number of results to return
 */
@ApiStatus.AvailableSince("1.0.0")
public record TransactionFilter(
        @Nullable TransactionType type,
        @Nullable Currency currency,
        @Nullable Instant after,
        @Nullable Instant before,
        int limit
) {
    /**
     * Convenience factory for "most recent N transactions, unfiltered".
     *
     * @param limit maximum number of results
     * @return a filter matching any transaction, capped at {@code limit}
     */
    public static @NotNull TransactionFilter recent(int limit) {
        return new TransactionFilter(null, null, null, null, limit);
    }
}
