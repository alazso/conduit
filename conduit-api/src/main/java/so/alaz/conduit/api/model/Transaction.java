package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable record of a committed economy operation.
 *
 * @param id            the unique identifier of this transaction
 * @param type          the kind of operation
 * @param actor         the player who initiated the operation, or {@code null}
 *                      for system/scheduled operations
 * @param target        the account affected
 * @param currency      the currency the operation was denominated in
 * @param amount        the magnitude moved (always non-negative)
 * @param balanceBefore the target balance prior to the operation
 * @param balanceAfter  the target balance after the operation
 * @param reason        a human-readable reason, or {@code null} if not provided
 * @param metadata      economy-scoped audit tags (e.g. {@code shop_id},
 *                      {@code item_id}) — <strong>not</strong> a general metadata
 *                      domain; defensively copied to preserve immutability
 * @param timestamp     when the operation committed
 */
@ApiStatus.AvailableSince("1.0.0")
public record Transaction(
        @NotNull UUID id,
        @NotNull TransactionType type,
        @Nullable UUID actor,
        @NotNull UUID target,
        @NotNull Currency currency,
        @NotNull BigDecimal amount,
        @NotNull BigDecimal balanceBefore,
        @NotNull BigDecimal balanceAfter,
        @Nullable String reason,
        @NotNull Map<String, String> metadata,
        @NotNull Instant timestamp
) {
    /**
     * Canonical constructor; defensively copies {@code metadata} so the record
     * stays immutable even if the caller mutates the supplied map afterwards.
     */
    public Transaction {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
