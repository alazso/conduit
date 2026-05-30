package so.alaz.conduit.api.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A typed success/failure result for operations that return a value on success.
 *
 * @param <T> the success value type
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface Result<T> permits Result.Success, Result.Failure {

    /**
     * A successful result carrying a value.
     *
     * @param value the success value
     * @param <T>   the value type
     */
    record Success<T>(T value) implements Result<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T getOrThrow() {
            return value;
        }

        @Override
        public T getOrDefault(T fallback) {
            return value;
        }

        @Override
        public @NotNull Optional<T> toOptional() {
            return Optional.ofNullable(value);
        }
    }

    /**
     * A failed result carrying a reason and optional cause.
     *
     * @param reason a human-readable failure reason
     * @param cause  the underlying cause, or {@code null}
     * @param <T>    the value type that would have been produced on success
     */
    record Failure<T>(@NotNull String reason, @Nullable Throwable cause) implements Result<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T getOrThrow() {
            throw new NoSuchElementException("Result failed: " + reason);
        }

        @Override
        public T getOrDefault(T fallback) {
            return fallback;
        }

        @Override
        public @NotNull Optional<T> toOptional() {
            return Optional.empty();
        }
    }

    /**
     * @return {@code true} if this is a {@link Success}
     */
    boolean isSuccess();

    /**
     * @return the success value
     * @throws NoSuchElementException if this is a {@link Failure}
     */
    T getOrThrow();

    /**
     * @param fallback the value to return on failure
     * @return the success value, or {@code fallback} on failure
     */
    T getOrDefault(T fallback);

    /**
     * @return an {@link Optional} of the success value, empty on failure
     */
    @NotNull Optional<T> toOptional();

    /**
     * Run {@code consumer} with the value if this is a success.
     *
     * @param consumer the success consumer
     * @return this result, for chaining
     */
    default @NotNull Result<T> ifSuccess(@NotNull Consumer<? super T> consumer) {
        if (this instanceof Success<T> success) {
            consumer.accept(success.value());
        }
        return this;
    }

    /**
     * Transform the success value, propagating failure unchanged.
     *
     * @param mapper the value mapper
     * @param <R>    the mapped value type
     * @return a mapped result
     */
    default <R> @NotNull Result<R> map(@NotNull Function<? super T, ? extends R> mapper) {
        return switch (this) {
            case Success<T> success -> new Success<>(mapper.apply(success.value()));
            case Failure<T> failure -> new Failure<>(failure.reason(), failure.cause());
        };
    }

    /**
     * Collapse this result to a single value.
     *
     * @param onSuccess maps the success value
     * @param onFailure maps the failure (reason, cause)
     * @param <R>       the folded type
     * @return the folded value
     */
    default <R> R fold(@NotNull Function<? super T, ? extends R> onSuccess,
                       @NotNull BiFunction<String, Throwable, ? extends R> onFailure) {
        return switch (this) {
            case Success<T> success -> onSuccess.apply(success.value());
            case Failure<T> failure -> onFailure.apply(failure.reason(), failure.cause());
        };
    }
}
