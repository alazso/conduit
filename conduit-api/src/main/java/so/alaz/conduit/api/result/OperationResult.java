package so.alaz.conduit.api.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import so.alaz.conduit.api.exception.OperationException;

import java.util.function.Consumer;

/**
 * Result of a void-returning fallible operation.
 *
 * <p>Replaces {@code Result<Void>}, whose only success inhabitant would be
 * {@code null}.
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface OperationResult permits OperationResult.Success, OperationResult.Failure {

    /** A successful operation. */
    record Success() implements OperationResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public void orThrow() {
            // no-op
        }
    }

    /**
     * A failed operation.
     *
     * @param reason a human-readable failure reason
     * @param cause  the underlying cause, or {@code null}
     */
    record Failure(@NotNull String reason, @Nullable Throwable cause) implements OperationResult {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public void orThrow() {
            throw new OperationException(this);
        }
    }

    /**
     * @return {@code true} if this is a {@link Success}
     */
    boolean isSuccess();

    /**
     * Throw if this result is a failure.
     *
     * @throws OperationException if this is a {@link Failure}
     */
    void orThrow() throws OperationException;

    /**
     * Run {@code action} if this operation succeeded.
     *
     * @param action the success action
     * @return this result, for chaining
     */
    default @NotNull OperationResult ifSuccess(@NotNull Runnable action) {
        if (isSuccess()) {
            action.run();
        }
        return this;
    }

    /**
     * Run {@code consumer} with the failure if this operation failed.
     *
     * @param consumer the failure consumer
     * @return this result, for chaining
     */
    default @NotNull OperationResult ifFailure(@NotNull Consumer<Failure> consumer) {
        if (this instanceof Failure failure) {
            consumer.accept(failure);
        }
        return this;
    }

    /**
     * @return a shared success result
     */
    static @NotNull OperationResult success() {
        return new Success();
    }

    /**
     * @param reason the failure reason
     * @return a failure result with no cause
     */
    static @NotNull OperationResult failure(@NotNull String reason) {
        return new Failure(reason, null);
    }

    /**
     * @param reason the failure reason
     * @param cause  the underlying cause
     * @return a failure result carrying {@code cause}
     */
    static @NotNull OperationResult failure(@NotNull String reason, @NotNull Throwable cause) {
        return new Failure(reason, cause);
    }
}
