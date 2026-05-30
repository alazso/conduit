package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries economy-scoped audit metadata from a {@link TransactionBuilder} into
 * the Conduit dispatch layer for the synchronous duration of an operation.
 *
 * <p>Mirrors the {@link so.alaz.conduit.api.caller.CallerToken} {@link ScopedValue}
 * pattern: the builder binds the metadata around the synchronous call into the
 * economy, the dispatcher captures it <em>before</em> the async operation
 * starts, and attaches it to the published
 * {@link so.alaz.conduit.api.event.EconomyTransactionEvent}'s transaction. The
 * value is only observable on the binding thread for the lifetime of the bound
 * action; continuations on other threads observe an empty map.
 *
 * <p>This is the channel that makes {@link TransactionBuilder#metadata(String, String)}
 * honoured rather than a silent no-op. It lives in {@code conduit-api} (not
 * {@code conduit-core}) so both the default builder and the runtime dispatcher
 * can reference it without the API depending on the runtime.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class TransactionContext {

    private static final ScopedValue<Map<String, String>> METADATA = ScopedValue.newInstance();

    private TransactionContext() {
    }

    /**
     * @return the metadata bound to the current scope, or an empty map if none
     */
    public static @NotNull Map<String, String> currentMetadata() {
        return METADATA.orElse(Map.of());
    }

    /**
     * Run a metadata-bound supplier, returning its value.
     *
     * @param metadata the metadata to bind (defensively copied)
     * @param action   the action to invoke with the metadata bound
     * @param <T>      the action's return type
     * @return the action's result
     */
    public static <T> T supplyWith(@NotNull Map<String, String> metadata, @NotNull Supplier<T> action) {
        // Java 25's stable ScopedValue.Carrier#call accepts a CallableOp; adapt
        // the Supplier via a method reference (it declares no checked exception).
        return ScopedValue.where(METADATA, Map.copyOf(metadata)).call(action::get);
    }
}
