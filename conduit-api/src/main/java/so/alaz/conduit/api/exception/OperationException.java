package so.alaz.conduit.api.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.result.OperationResult;

/**
 * Thrown by {@link OperationResult#orThrow()} when the result is a failure.
 */
@ApiStatus.AvailableSince("1.0.0")
public class OperationException extends RuntimeException {

    private final transient OperationResult result;

    /**
     * @param failure the failing operation result
     */
    public OperationException(OperationResult.@NotNull Failure failure) {
        super(failure.reason(), failure.cause());
        this.result = failure;
    }

    /**
     * @return the originating operation result
     */
    public @NotNull OperationResult result() {
        return result;
    }
}
