package so.alaz.conduit.api.economy.support;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.exception.IdempotencyMismatchException;
import so.alaz.conduit.api.result.EconomyResult;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Reusable, thread-safe implementation of the
 * {@link so.alaz.conduit.api.economy.TransactionalEconomy} idempotency contract,
 * so providers do not each re-implement deduplication.
 *
 * <p>Uniqueness is scoped <strong>per account</strong>, matching the normative
 * contract: the same {@code operationId} on a different primary account executes
 * independently. Resubmitting with a matching {@code fingerprint} returns the
 * original {@link EconomyResult} verbatim without re-executing; resubmitting
 * with a different fingerprint throws {@link IdempotencyMismatchException}.
 *
 * <p>The {@code fingerprint} should capture exactly the operation's intrinsic
 * parameters (amount, currency, counterpart account) and nothing descriptive
 * (reason/metadata). {@link #fingerprint(Object...)} builds a suitable value.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class IdempotencyStore {

    private record Key(UUID account, UUID operationId) {
    }

    private record Entry(Object fingerprint, EconomyResult result) {
    }

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Execute {@code operation} unless it has already run for this
     * {@code (account, operationId)} pair.
     *
     * @param account     the primary account the operation targets (uniqueness scope)
     * @param operationId the idempotency key
     * @param fingerprint the operation's intrinsic-parameter fingerprint
     * @param operation   the operation to run on first submission
     * @return the original result on replay, or a freshly executed result
     * @throws IdempotencyMismatchException if the id is reused with a different fingerprint
     */
    public @NotNull EconomyResult execute(
            @NotNull UUID account, @NotNull UUID operationId,
            @NotNull Object fingerprint, @NotNull Supplier<EconomyResult> operation) {

        Key key = new Key(account, operationId);
        Entry existing = entries.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IdempotencyMismatchException(operationId,
                        "parameters differ from the original submission for account " + account);
            }
            return existing.result();
        }
        EconomyResult result = operation.get();
        // Only the first writer wins; a concurrent duplicate must observe the
        // same result rather than overwriting it.
        Entry race = entries.putIfAbsent(key, new Entry(fingerprint, result));
        if (race != null) {
            if (!race.fingerprint().equals(fingerprint)) {
                throw new IdempotencyMismatchException(operationId,
                        "parameters differ from the original submission for account " + account);
            }
            return race.result();
        }
        return result;
    }

    /**
     * Build an order-sensitive fingerprint from the operation's intrinsic
     * parameters. {@code BigDecimal} arguments should be normalised by the
     * caller if scale-insensitivity is required.
     *
     * @param parts the intrinsic parameters
     * @return a value suitable as a fingerprint
     */
    public static @NotNull Object fingerprint(@NotNull Object... parts) {
        return java.util.List.of(java.util.Arrays.stream(parts)
                .map(p -> Objects.requireNonNull(p, "fingerprint part"))
                .toArray());
    }

    /**
     * @return the number of recorded operations
     */
    public int size() {
        return entries.size();
    }
}
