package so.alaz.conduit.api.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Carried by the failed {@code CompletableFuture} returned from a
 * {@code TransactionalEconomy.*Idempotent} call when the same
 * {@code operationId} is resubmitted with parameters that do not match the
 * original submission.
 *
 * <p>This is a corruption-detection path, not undefined behaviour: it signals
 * either incorrect deduplication keying or two distinct operations colliding on
 * an id.
 */
@ApiStatus.AvailableSince("1.0.0")
public class IdempotencyMismatchException extends RuntimeException {

    private final transient UUID operationId;

    /**
     * @param operationId the conflicting operation id
     * @param message     a description of the mismatch
     */
    public IdempotencyMismatchException(@NotNull UUID operationId, @NotNull String message) {
        super("operationId " + operationId + ": " + message);
        this.operationId = operationId;
    }

    /**
     * @return the conflicting operation id
     */
    public @NotNull UUID operationId() {
        return operationId;
    }
}
