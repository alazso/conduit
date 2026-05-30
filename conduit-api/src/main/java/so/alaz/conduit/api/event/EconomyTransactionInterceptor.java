package so.alaz.conduit.api.event;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.model.Currency;
import so.alaz.conduit.api.model.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous pre-authorisation hook for economy operations.
 *
 * <p>Runs before the async operation starts, on the thread that initiated the
 * economy call (on Folia, the entity's region thread; on Paper, typically the
 * main thread). Implementations must be thread-safe and must not block. Return
 * {@code false} to abort — no {@link EconomyTransactionEvent} will fire.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface EconomyTransactionInterceptor {

    /**
     * @param context the operation about to be attempted
     * @return {@code true} to allow, {@code false} to abort
     */
    boolean intercept(@NotNull InterceptContext context);

    /**
     * The operation about to be attempted.
     *
     * @param target   the account affected
     * @param amount   the operation magnitude
     * @param type     the operation type
     * @param currency the currency involved
     * @param caller   the initiating caller
     */
    record InterceptContext(
            @NotNull UUID target,
            @NotNull BigDecimal amount,
            @NotNull TransactionType type,
            @NotNull Currency currency,
            @NotNull CallerToken caller
    ) {}
}
